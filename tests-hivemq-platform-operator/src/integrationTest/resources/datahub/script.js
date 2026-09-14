function transform(publish, context) {
    // adds timestamp as ISO string
    publish.payload.timestamp = new Date().toJSON()
    return publish;
}
