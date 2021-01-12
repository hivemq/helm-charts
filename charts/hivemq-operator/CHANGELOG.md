# 4.5.0

- BREAKING: Changed default transport type to TCP. Note that if you are upgrading, you must set the transport explicitly to UDP or re-create (backup, uninstall, install) your cluster to switch to TCP transport.
- Reduce `cpuLimitRatio` default value to 1 for a more predictable CPU count seen by HiveMQ.
- Added safe sysctl settings by default to the HiveMQ Pods' security context which will extend the local port range
- Add proper templating and default example for the initialization field.
- Add nodeSelector support to operator deployment template
- Increased liveness `failureThreshold` to ensure joining HiveMQ nodes don't get shut down during a long-lasting join process
- Fix templating of multi-line environment variables
- Add a default heap dump storage path and volume, to preserve heap dump files after container restarts (requires HiveMQ 4.4.3+)
- Fix image pull secrets not being used in generated custom resource
- Improve service monitor naming to exactly match the generated cluster name, for easier correlation when querying metrics
- Migrate validation hook TLS provisioning to webhook cert generator to make validation hook configuration more reliable
- Move image pull secrets to global section
- Introduce more detailed webhook configuration options
- Add namespace override
