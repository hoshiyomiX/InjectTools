use crate::dns::{self, DnsResolver};
use anyhow::Result;
use colored::*;
use futures::stream::{FuturesUnordered, StreamExt};
use indicatif::{ProgressBar, ProgressStyle};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::net::IpAddr;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScanResult {
    pub subdomain: String,
    pub ip: String,
    pub works: bool,
}

#[derive(Debug)]
pub struct BatchResult {
    pub working: Vec<ScanResult>,
    pub failed: Vec<ScanResult>,
    pub skipped: usize,
    pub total_scanned: usize,
    pub elapsed_secs: u64,
}

pub struct Scanner {
    target_host: String,
    timeout: u64,
    client: Client,
}

impl Scanner {
    pub fn new(target_host: String, timeout: u64) -> Self {
        let client = Client::builder()
            .timeout(Duration::from_secs(timeout))
            .danger_accept_invalid_certs(true)
            .build()
            .expect("Failed to create HTTP client");

        Self {
            target_host,
            timeout,
            client,
        }
    }

    pub async fn resolve_domain(&self, domain: &str) -> Option<IpAddr> {
        let resolver = DnsResolver::new();
        resolver.resolve(domain).await.ok().flatten()
    }

    /// Ping test with platform-specific implementation
    /// - Non-Android: Uses ICMP ping via surge-ping
    /// - Android: Uses HTTP response time as fallback (no root required)
    pub async fn ping_test(&self, domain: &str) -> Option<u128> {
        #[cfg(not(target_os = "android"))]
        {
            use surge_ping::{Client, Config, PingIdentifier, PingSequence, ICMP};

            let client = Client::new(&Config::default()).ok()?;
            let mut pinger = client
                .pinger(domain.parse().ok()?, PingIdentifier(rand::random()))
                .await;

            match pinger
                .ping(PingSequence(0), &[])
                .await
                .ok()?
                .1
            {
                Some((_packet, duration)) => Some(duration.as_millis()),
                None => None,
            }
        }

        #[cfg(target_os = "android")]
        {
            // Android fallback: HTTP response time
            let start = Instant::now();
            let url = format!("https://{}", domain);

            match self
                .client
                .get(&url)
                .timeout(Duration::from_secs(5))
                .send()
                .await
            {
                Ok(_) => Some(start.elapsed().as_millis()),
                Err(_) => None,
            }
        }
    }

    pub async fn test_inject(&self, ip: &IpAddr, subdomain: &str) -> bool {
        let url = format!("https://{}/", self.target_host);

        let client = Client::builder()
            .timeout(Duration::from_secs(self.timeout))
            .danger_accept_invalid_certs(true)
            .resolve(&self.target_host, format!("{}:443", ip).parse().unwrap())
            .build();

        match client {
            Ok(client) => match client.get(&url).header("Host", subdomain).send().await {
                Ok(resp) => resp.status().is_success() || resp.status().is_redirection(),
                Err(_) => false,
            },
            Err(_) => false,
        }
    }

    pub async fn batch_test(
        &self,
        subdomains: Vec<String>,
        cancelled: Arc<AtomicBool>,
    ) -> Result<BatchResult> {
        let start = Instant::now();
        let total = subdomains.len();

        let progress = Arc::new(ProgressBar::new(total as u64));
        progress.set_style(
            ProgressStyle::default_bar()
                .template(
                    "{spinner:.cyan} [{bar:40.cyan/blue}] {pos}/{len} ({percent}%) {msg}",
                )?
                .progress_chars("#>-"),
        );

        let working = Arc::new(std::sync::Mutex::new(Vec::new()));
        let failed = Arc::new(std::sync::Mutex::new(Vec::new()));
        let scanned = Arc::new(AtomicUsize::new(0));

        let mut tasks = FuturesUnordered::new();

        for subdomain in subdomains {
            if cancelled.load(Ordering::SeqCst) {
                break;
            }

            let subdomain = subdomain.clone();
            let progress = Arc::clone(&progress);
            let working = Arc::clone(&working);
            let failed = Arc::clone(&failed);
            let scanned = Arc::clone(&scanned);
            let cancelled = Arc::clone(&cancelled);
            let target_host = self.target_host.clone();
            let timeout = self.timeout;

            tasks.push(tokio::spawn(async move {
                if cancelled.load(Ordering::SeqCst) {
                    return;
                }

                progress.set_message(format!("Testing: {}", subdomain));

                let resolver = DnsResolver::new();
                if let Ok(Some(ip)) = resolver.resolve(&subdomain).await {
                    if dns::is_cloudflare(&ip) {
                        let scanner = Scanner::new(target_host, timeout);
                        let result = scanner.test_inject(&ip, &subdomain).await;

                        let scan_result = ScanResult {
                            subdomain: subdomain.clone(),
                            ip: ip.to_string(),
                            works: result,
                        };

                        if result {
                            working.lock().unwrap().push(scan_result);
                        } else {
                            failed.lock().unwrap().push(scan_result);
                        }
                    }
                }

                scanned.fetch_add(1, Ordering::SeqCst);
                progress.inc(1);
            }));

            // Limit concurrent tasks
            if tasks.len() >= 50 {
                tasks.next().await;
            }
        }

        while tasks.next().await.is_some() {}

        progress.finish_and_clear();

        let working = Arc::try_unwrap(working)
            .unwrap()
            .into_inner()
            .unwrap();
        let failed = Arc::try_unwrap(failed).unwrap().into_inner().unwrap();

        Ok(BatchResult {
            working,
            failed,
            skipped: 0,
            total_scanned: scanned.load(Ordering::SeqCst),
            elapsed_secs: start.elapsed().as_secs(),
        })
    }

    pub async fn full_scan(
        &self,
        subdomains: Vec<String>,
        cancelled: Arc<AtomicBool>,
    ) -> Result<BatchResult> {
        self.batch_test(subdomains, cancelled).await
    }
}
