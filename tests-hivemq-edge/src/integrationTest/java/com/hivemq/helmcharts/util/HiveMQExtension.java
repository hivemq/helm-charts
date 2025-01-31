package com.hivemq.helmcharts.util;

import com.hivemq.extension.sdk.api.ExtensionMain;
import javassist.ClassPool;
import javassist.NotFoundException;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Creates a HiveMQ extension ZIP from a given extension main class.
 * <p>
 * Compiles classes that are located in the specified source folder with Java version {@value JAVA_VERSION}.
 * <p>
 * Note: The ad-hoc compilation doesn't support nested classes!
 *
 * @see <a
 *         href="https://github.com/testcontainers/testcontainers-java/blob/main/modules/hivemq/src/main/java/org/testcontainers/hivemq/HiveMQExtension.java">Testcontainers</a>
 */
public class HiveMQExtension {

    private static final @NotNull String JAVA_VERSION = "11";
    private static final @NotNull String DEFAULT_SOURCE_FOLDER = "integrationTest";
    private static final @NotNull String EXTENSION_MAIN_CLASS_NAME = "com.hivemq.extension.sdk.api.ExtensionMain";
    private static final @NotNull String VALID_EXTENSION_XML = "<hivemq-extension>\n" + //
            "   <id>%s</id>\n" + //
            "   <name>%s</name>\n" + //
            "   <version>%s</version>\n" + //
            "   <priority>%s</priority>\n" + //
            "   <start-priority>%s</start-priority>\n" + //
            "</hivemq-extension>\n";

    private static final @NotNull Logger LOGGER = LoggerFactory.getLogger(HiveMQExtension.class);

    private final @NotNull String extensionId;
    private final @NotNull String name;
    private final @NotNull String version;
    private final @NotNull Path sourceFolder;
    private final int priority;
    private final int startPriority;
    private final boolean disabledOnStartup;
    private final @NotNull Class<?> mainClass;
    private final @NotNull List<Class<?>> additionalClasses;

    private HiveMQExtension(
            final @NotNull String extensionId,
            final @NotNull String name,
            final @NotNull String version,
            final @NotNull String sourceFolder,
            final int priority,
            final int startPriority,
            final boolean disabledOnStartup,
            final @NotNull Class<? extends ExtensionMain> mainClass,
            final @NotNull List<Class<?>> additionalClasses) {
        this.extensionId = extensionId;
        this.name = name;
        this.version = version;
        this.priority = priority;
        this.startPriority = startPriority;
        this.disabledOnStartup = disabledOnStartup;
        this.mainClass = mainClass;
        this.additionalClasses = additionalClasses;
        this.sourceFolder = Path.of("").toAbsolutePath().resolve("src").resolve(sourceFolder).resolve("java");
    }

    public static @NotNull Path createHiveMQExtensionZip(
            final @NotNull Path tempDir,
            final @NotNull String extensionId,
            final @NotNull String extensionName,
            final @NotNull String version,
            final @NotNull Class<? extends ExtensionMain> extensionMainClass) throws Exception {
        final var hiveMQExtension = new Builder() //
                .id(extensionId) //
                .name(extensionName) //
                .version(version) //
                .mainClass(extensionMainClass) //
                .build();
        return hiveMQExtension.createExtensionZip(tempDir);
    }

    private @NotNull Path createExtensionZip(final @NotNull Path tempDir) throws Exception {
        final var extensionDir = tempDir.resolve(extensionId + "-" + version);
        Files.createDirectory(extensionDir);

        // JDK compiler
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var fileManager = compiler.getStandardFileManager(null, null, UTF_8);
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(extensionDir.toFile()));
        final var compilerOptions = List.of("--release", JAVA_VERSION, "-Xlint:-options");

        // extension.zip
        final var extensionZip = tempDir.resolve(extensionId + "-" + version + ".zip");
        final var extensionZipArchive = ShrinkWrap.create(JavaArchive.class);

        // hivemq-extension.xml
        final var extensionXml = extensionDir.resolve("hivemq-extension.xml");
        Files.createFile(extensionXml);
        Files.writeString(extensionXml,
                String.format(VALID_EXTENSION_XML, extensionId, name, version, priority, startPriority));
        extensionZipArchive.add(new FileAsset(extensionXml.toFile()),
                extensionId + "/" + extensionXml.getFileName().toString());

        // DISABLED file
        if (disabledOnStartup) {
            final var disabled = extensionDir.resolve("DISABLED");
            Files.createFile(disabled);
            extensionZipArchive.add(new FileAsset(disabled.toFile()),
                    extensionId + "/" + disabled.getFileName().toString());
        }

        // extension.jar
        final var extensionJarArchive = ShrinkWrap.create(JavaArchive.class)
                .addAsServiceProvider(EXTENSION_MAIN_CLASS_NAME, mainClass.getName());
        addSubclassesToJar(fileManager, compiler, compilerOptions, mainClass.getCanonicalName(), extensionJarArchive);
        for (final var additionalClass : additionalClasses) {
            addSubclassesToJar(fileManager,
                    compiler,
                    compilerOptions,
                    additionalClass.getCanonicalName(),
                    extensionJarArchive);
        }
        final var extensionJar = extensionDir.resolve(extensionId + "-" + version + ".jar");
        extensionJarArchive.as(ZipExporter.class).exportTo(extensionJar.toFile());
        extensionZipArchive.add(new FileAsset(extensionJar.toFile()),
                extensionId + "/" + extensionJar.getFileName().toString());

        extensionZipArchive.as(ZipExporter.class).exportTo(extensionZip.toFile());
        return extensionZip;
    }

    private void addSubclassesToJar(
            final @NotNull StandardJavaFileManager fileManager,
            final @NotNull JavaCompiler compiler,
            final @NotNull List<String> compilerOptions,
            final @NotNull String classToCompile,
            final @NotNull JavaArchive javaArchive) throws NotFoundException {
        for (final var subClassName : ClassPool.getDefault()
                .get(classToCompile)
                .getClassFile()
                .getConstPool()
                .getClassNames()) {
            final var className = subClassName.replaceAll("/", ".");
            // skip JDK classes
            if (subClassName.startsWith("java/") || subClassName.startsWith("jdk/")) {
                LOGGER.trace("Skipping JDK class '{}' for extension '{}'", className, extensionId);
                continue;
            }
            // skip arrays
            if (subClassName.startsWith("[L")) {
                LOGGER.trace("Skipping array class '{}' for extension '{}'", className, extensionId);
                continue;
            }
            // add class
            if (Files.exists(sourceFolder.resolve(subClassName + ".java"))) {
                // this is a class from our source folder, so it needs to be compiled
                compileClass(fileManager, compiler, compilerOptions, subClassName, javaArchive);
                if (!className.equals(classToCompile)) {
                    addSubclassesToJar(fileManager, compiler, compilerOptions, className, javaArchive);
                }
            } else {
                // this is an external class, it can directly be added
                LOGGER.debug("Trying to package class '{}' into extension '{}'", className, extensionId);
                javaArchive.addClass(className);
            }
        }
    }

    private void compileClass(
            final @NotNull StandardJavaFileManager fileManager,
            final @NotNull JavaCompiler compiler,
            final @NotNull List<String> compilerOptions,
            final @NotNull String classToCompile,
            final @NotNull JavaArchive javaArchive) {
        final var javaSourcePath = sourceFolder.resolve(classToCompile + ".java");
        LOGGER.debug("Trying to compile class '{}' into extension '{}'", javaSourcePath, extensionId);
        final var compilationUnits = fileManager.getJavaFileObjectsFromFiles(List.of(javaSourcePath.toFile()));
        final var compilerTask = compiler.getTask(null, fileManager, null, compilerOptions, null, compilationUnits);
        if (!compilerTask.call()) {
            throw new IllegalStateException(String.format("Could not compile class '%s' into extension '%s'",
                    classToCompile,
                    extensionId));
        }
        fileManager.getLocation(StandardLocation.CLASS_OUTPUT)
                .forEach(compileDir -> javaArchive.add(new FileAsset(compileDir.toPath()
                        .resolve(classToCompile + ".class")
                        .toFile()), classToCompile + ".class"));
    }

    @SuppressWarnings("unused")
    private static final class Builder {

        private final @NotNull LinkedList<Class<?>> additionalClasses = new LinkedList<>();

        private @Nullable String extensionId;
        private @Nullable String name;
        private @Nullable String version;
        private @NotNull String sourceFolder = DEFAULT_SOURCE_FOLDER;
        private int priority = 0;
        private int startPriority = 1000;
        private boolean disabledOnStartup = false;
        private @Nullable Class<? extends ExtensionMain> mainClass;

        /**
         * Sets the identifier of the {@link HiveMQExtension}.
         *
         * @param extensionId the extension identifier, must not be blank
         * @return the {@link Builder}
         */
        private @NotNull Builder id(final @NotNull String extensionId) {
            this.extensionId = extensionId;
            return this;
        }

        /**
         * Sets the name of the {@link HiveMQExtension}.
         *
         * @param name the identifier, must not be blank
         * @return the {@link Builder}
         */
        private @NotNull Builder name(final @NotNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the version of the {@link HiveMQExtension}.
         *
         * @param version the version, must not be blank
         * @return the {@link Builder}
         */
        private @NotNull Builder version(final @NotNull String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the source folder of the {@link HiveMQExtension} class.
         * <p>
         * Will be used in {@code <projectRoot>/src/<sourceFolder>/java/}.
         *
         * @param sourceFolder the source folder, must not be blank
         * @return the {@link Builder}
         */
        private @NotNull Builder sourceFolder(final @NotNull String sourceFolder) {
            this.sourceFolder = sourceFolder;
            return this;
        }

        /**
         * Sets the priority of the {@link HiveMQExtension}.
         *
         * @param priority the priority
         * @return the {@link Builder}
         */
        private @NotNull Builder priority(final int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets the start-priority of the {@link HiveMQExtension}.
         *
         * @param startPriority the start-priority
         * @return the {@link Builder}
         */
        private @NotNull Builder startPriority(final int startPriority) {
            this.startPriority = startPriority;
            return this;
        }

        /**
         * Flag, that indicates whether the {@link HiveMQExtension} should be disabled when HiveMQ starts.
         * Disabling on startup is achieved by placing a DISABLED file in the {@link HiveMQExtension}'s directory before
         * coping it to the container.
         *
         * @param disabledOnStartup if the {@link HiveMQExtension} should be disabled when HiveMQ starts
         * @return the {@link Builder}
         */
        private @NotNull Builder disabledOnStartup(final boolean disabledOnStartup) {
            this.disabledOnStartup = disabledOnStartup;
            return this;
        }

        /**
         * The main class of the {@link HiveMQExtension}.
         * This class MUST implement com.hivemq.extension.sdk.api.ExtensionMain.
         *
         * @param mainClass the main class
         * @return the {@link Builder}
         * @throws IllegalArgumentException if the provides class does not implement
         *                                  com.hivemq.extension.sdk.api.ExtensionMain}
         * @throws IllegalStateException    if com.hivemq.extension.sdk.api.ExtensionMain is not found in the classpath
         */
        private @NotNull Builder mainClass(final @NotNull Class<? extends ExtensionMain> mainClass) {
            try {
                final var extensionMain = Class.forName(EXTENSION_MAIN_CLASS_NAME);
                this.mainClass = mainClass;
                return this;
            } catch (final ClassNotFoundException e) {
                throw new IllegalStateException("The class '" +
                        EXTENSION_MAIN_CLASS_NAME +
                        "' was not found in the classpath.");
            }
        }

        /**
         * Adds additional classes to the JAR file of the {@link HiveMQExtension}.
         *
         * @param clazz the additional class
         * @return the {@link Builder}
         */
        private @NotNull Builder addAdditionalClass(final @NotNull Class<?> clazz) {
            this.additionalClasses.add(clazz);
            return this;
        }

        /**
         * Builds the {@link HiveMQExtension} with the provided values or default values.
         *
         * @return the HiveMQ Extension
         */
        private @NotNull HiveMQExtension build() {
            if (extensionId == null || extensionId.isBlank()) {
                throw new IllegalArgumentException("extension id must not be null or blank");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("extension name must not be null or blank");
            }
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("extension version must not be null or blank");
            }
            if (mainClass == null) {
                throw new IllegalArgumentException("extension main class must not be null");
            }
            return new HiveMQExtension(extensionId,
                    name,
                    version,
                    sourceFolder,
                    priority,
                    startPriority,
                    disabledOnStartup,
                    mainClass,
                    additionalClasses);
        }
    }
}
