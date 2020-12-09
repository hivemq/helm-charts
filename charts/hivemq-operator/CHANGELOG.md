# 4.5.0

- Added safe sysctl settings by default to the HiveMQ Pods' security context which will extend the local port range
- Added a separate Deployment controller template that sets some unsafe sysctls (You will have to reconfigure kubelet to use this)
- Add proper templating and default example for the initContainer field.
- Add nodeSelector support to operator deployment template
- Increased liveness `failureThreshold` to ensure joining HiveMQ nodes don't get shut down during a long-lasting join process
- Fix templating of multi-line environment variables
- Add a default heap dump storage path and volume, to preserve heap dump files after container restarts (requires HiveMQ 4.4.3+)
- Fix image pull secrets not being used in generated custom resource
- Add field for adding annotations to the operator service account
- Improve service monitor naming to exactly match the generated cluster name, for easier correlation when querying metrics
- Migrate validation hook TLS provisioning to webhook cert generator
- Move image pull secrets to global section
- Introduce more detailed webhook configuration option
- Add namespace override
- Reduce `cpuLimitRatio` default value to 1 for a more predictable CPU count seen by HiveMQ.
