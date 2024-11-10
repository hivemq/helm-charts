package com.hivemq.release;

import com.beust.jcommander.Parameter;
import org.jetbrains.annotations.NotNull;

class Arguments {

    @Parameter(names = {"--help", "-h"}, description = "Prints the usage.", help = true)
    boolean help;

    @Parameter(names = {"--github-cli", "-g"}, description = "Path to the GitHub CLI binary.", required = true)
    @SuppressWarnings("NotNullFieldNotInitialized")
    @NotNull String gitHubCliPath;

    @Parameter(names = {"--path", "-p"}, description = "Path to input files.", required = true)
    @SuppressWarnings("NotNullFieldNotInitialized")
    @NotNull String path;
}
