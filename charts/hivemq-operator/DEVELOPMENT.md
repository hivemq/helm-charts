## Development

### Updating the `hivemq` structure

The `hivemq` substructure within values.yaml and its default values are generated from the operator CRD's OpenAPI specification. (Can be found in [hivemqCluster.json](https://github.com/hivemq/hivemq-operator/tree/master/operator/src/main/resources/schema/hivemqCluster.json))

To update the values, run `./gradlew generateSchemaDefaults` from the operator project and then copy the contents of the generated `defaults.yaml` to the values.yaml file.

### Updating the `crds` folder

The CustomResourceDefinition YAML is also generated from the operator project.
Run `./gradlew generateCustomResource` in the operator directory to directly update the `crds/` folder.

### Other co-dependent files

- operator-tmpls must be exactly the same as the operator repo's `src/main/templates`
- hivemqCluster.json must be equal to operator repo's
