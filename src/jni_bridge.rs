//! JNI Bridge Module
//!
//! Provides FFI (Foreign Function Interface) exports for Android JNI integration.
//! This allows Kotlin/Java code to call Rust functions directly.
//!
//! Architecture:
//! ```
//! Kotlin UI Layer
//!      ↓ (JNI Call)
//! JNI Bridge (this module)
//!      ↓ (Async via Tokio)
//! Scanner Core Logic
//! ```
//!
//! All functions are thread-safe and use a global Tokio runtime
//! to handle async operations synchronously from the JNI layer.

use jni::JNIEnv;
use jni::objects::{JClass, JString, JObject, JObjectArray};
use jni::sys::{jstring, jboolean, jint};
use std::sync::Arc;
use tokio::runtime::Runtime;
use lazy_static::lazy_static;

// Import scanner functions
use crate::scanner;
use crate::dns;

// ═══════════════════════════════════════════════════════════════════════════════════
// GLOBAL TOKIO RUNTIME
// ═══════════════════════════════════════════════════════════════════════════════════

lazy_static! {
    /// Global Tokio runtime for handling async operations from JNI
    /// 
    /// This is created once and shared across all JNI calls.
    /// Using a single runtime is more efficient than creating one per call.
    static ref RUNTIME: Arc<Runtime> = Arc::new(
        Runtime::new().expect("Failed to create Tokio runtime for JNI")
    );
}

// ═══════════════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════════

/// Convert Java String to Rust String (helper)
fn jstring_to_string(env: &mut JNIEnv, jstr: &JString) -> Result<String, String> {
    env.get_string(jstr)
        .map(|s| s.into())
        .map_err(|e| format!("Failed to convert JString: {}", e))
}

/// Convert Rust String to Java String (helper)
fn string_to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(jstr) => jstr.into_raw(),
        Err(e) => {
            eprintln!("Failed to create JString: {}", e);
            JString::default().into_raw()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════
// JNI EXPORTED FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════════

/// Check if target host is online and reachable
/// 
/// Java Signature: 
/// ```java
/// public static native boolean checkTargetOnline(String target);
/// ```
#[no_mangle]
pub extern "C" fn Java_com_hoshiyomi_injecttools_core_InjectToolsNative_checkTargetOnline(
    mut env: JNIEnv,
    _class: JClass,
    target: JString,
) -> jboolean {
    // Convert Java String to Rust String
    let target_str = match jstring_to_string(&mut env, &target) {
        Ok(s) => s,
        Err(_) => return 0, // false
    };
    
    // Execute async operation in blocking context
    let result = RUNTIME.block_on(async {
        scanner::check_target_online_native(&target_str).await
    });
    
    if result { 1 } else { 0 }
}

/// Test single subdomain against target
/// 
/// Java Signature:
/// ```java
/// public static native String testSubdomain(String target, String subdomain, int timeout);
/// ```
/// 
/// Returns: JSON string with ScanResult
#[no_mangle]
pub extern "C" fn Java_com_hoshiyomi_injecttools_core_InjectToolsNative_testSubdomain(
    mut env: JNIEnv,
    _class: JClass,
    target: JString,
    subdomain: JString,
    timeout: jint,
) -> jstring {
    // Convert Java Strings
    let target_str = match jstring_to_string(&mut env, &target) {
        Ok(s) => s,
        Err(e) => {
            let error_json = format!(r#"{{"error":"{}"}}", e);
            return string_to_jstring(&mut env, &error_json);
        }
    };
    
    let subdomain_str = match jstring_to_string(&mut env, &subdomain) {
        Ok(s) => s,
        Err(e) => {
            let error_json = format!(r#"{{"error":"{}"}}", e);
            return string_to_jstring(&mut env, &error_json);
        }
    };
    
    // Execute scan
    let result = RUNTIME.block_on(async {
        scanner::test_single_native(
            &target_str,
            &subdomain_str,
            timeout as u64
        ).await
    });
    
    // Serialize to JSON
    let json_result = match serde_json::to_string(&result) {
        Ok(json) => json,
        Err(e) => format!(r#"{{"error":"Serialization failed: {}"}}", e),
    };
    
    string_to_jstring(&mut env, &json_result)
}

/// Batch test multiple subdomains
/// 
/// Java Signature:
/// ```java
/// public static native String batchTest(String target, String[] subdomains, int timeout);
/// ```
/// 
/// Returns: JSON array string with List<ScanResult>
#[no_mangle]
pub extern "C" fn Java_com_hoshiyomi_injecttools_core_InjectToolsNative_batchTest(
    mut env: JNIEnv,
    _class: JClass,
    target: JString,
    subdomains: JObjectArray,
    timeout: jint,
) -> jstring {
    // Convert target
    let target_str = match jstring_to_string(&mut env, &target) {
        Ok(s) => s,
        Err(e) => {
            let error_json = format!(r#"[{{"error":"{}"}}]", e);
            return string_to_jstring(&mut env, &error_json);
        }
    };
    
    // Convert String[] to Vec<String>
    let len = match env.get_array_length(&subdomains) {
        Ok(l) => l,
        Err(_) => 0,
    };
    
    let mut subdomain_list = Vec::new();
    for i in 0..len {
        if let Ok(jstr) = env.get_object_array_element(&subdomains, i) {
            let jstring: JString = jstr.into();
            if let Ok(rust_str) = jstring_to_string(&mut env, &jstring) {
                subdomain_list.push(rust_str);
            }
        }
    }
    
    // Execute batch scan
    let results = RUNTIME.block_on(async {
        scanner::batch_test_native(
            &target_str,
            &subdomain_list,
            timeout as u64
        ).await
    });
    
    // Serialize results
    let json_results = match serde_json::to_string(&results) {
        Ok(json) => json,
        Err(e) => format!(r#"[{{"error":"Serialization failed: {}"}}]", e),
    };
    
    string_to_jstring(&mut env, &json_results)
}

/// Discover subdomains from crt.sh
/// 
/// Java Signature:
/// ```java
/// public static native String discoverSubdomains(String domain, int limit);
/// ```
/// 
/// Returns: JSON array of subdomain strings
#[no_mangle]
pub extern "C" fn Java_com_hoshiyomi_injecttools_core_InjectToolsNative_discoverSubdomains(
    mut env: JNIEnv,
    _class: JClass,
    domain: JString,
    limit: jint,
) -> jstring {
    // Convert domain
    let domain_str = match jstring_to_string(&mut env, &domain) {
        Ok(s) => s,
        Err(e) => {
            let error_json = format!(r#"{{"error":"{}"}}", e);
            return string_to_jstring(&mut env, &error_json);
        }
    };
    
    // Fetch subdomains
    let subdomains = RUNTIME.block_on(async {
        match crate::crtsh::fetch_subdomains(&domain_str).await {
            Ok(mut subs) => {
                // Limit results if specified
                if limit > 0 {
                    subs.truncate(limit as usize);
                }
                subs
            }
            Err(e) => {
                eprintln!("Failed to fetch from crt.sh: {}", e);
                Vec::new()
            }
        }
    });
    
    // Serialize to JSON array
    let json_array = match serde_json::to_string(&subdomains) {
        Ok(json) => json,
        Err(e) => format!(r#"{{"error":"Serialization failed: {}"}}", e),
    };
    
    string_to_jstring(&mut env, &json_array)
}

/// Resolve domain to IP address
/// 
/// Java Signature:
/// ```java
/// public static native String resolveDomain(String domain);
/// ```
/// 
/// Returns: IP address string or empty string on error
#[no_mangle]
pub extern "C" fn Java_com_hoshiyomi_injecttools_core_InjectToolsNative_resolveDomain(
    mut env: JNIEnv,
    _class: JClass,
    domain: JString,
) -> jstring {
    let domain_str = match jstring_to_string(&mut env, &domain) {
        Ok(s) => s,
        Err(_) => return string_to_jstring(&mut env, ""),
    };
    
    let ip = RUNTIME.block_on(async {
        dns::resolve_domain_first(&domain_str)
            .await
            .unwrap_or_default()
    });
    
    string_to_jstring(&mut env, &ip)
}

/// Check if IP is Cloudflare
/// 
/// Java Signature:
/// ```java
/// public static native boolean isCloudflareIP(String ip);
/// ```
#[no_mangle]
pub extern "C" fn Java_com_hoshiyomi_injecttools_core_InjectToolsNative_isCloudflareIP(
    mut env: JNIEnv,
    _class: JClass,
    ip: JString,
) -> jboolean {
    let ip_str = match jstring_to_string(&mut env, &ip) {
        Ok(s) => s,
        Err(_) => return 0,
    };
    
    if dns::is_cloudflare_ip(&ip_str) { 1 } else { 0 }
}

// ═══════════════════════════════════════════════════════════════════════════════════
// MODULE INITIALIZATION
// ═══════════════════════════════════════════════════════════════════════════════════

/// JNI_OnLoad - Called when library is loaded
/// 
/// This is optional but can be used for initialization.
#[no_mangle]
pub extern "system" fn JNI_OnLoad(
    _vm: *mut jni::sys::JavaVM,
    _reserved: *mut std::ffi::c_void,
) -> jni::sys::jint {
    // Initialize logger if needed
    // env_logger::init();
    
    // Return JNI version
    jni::sys::JNI_VERSION_1_6
}
