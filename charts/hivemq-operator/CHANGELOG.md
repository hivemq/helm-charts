# 4.5.0

- Added safe sysctl settings by default to the HiveMQ Pods' security context
- Added a separate Deployment controller template that sets some initial unsafe sysctls as well
- Add support for custom sidecar containers that run alongside your HiveMQ deployment
- Add proper templating and default example for the initContainer field.
- Add nodeSelector support to operator deployment template
- Increased liveness `failureThreshold` to ensure joining HiveMQ nodes don't get shut down during join
