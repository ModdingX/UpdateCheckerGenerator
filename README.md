# UpdateCheckerGenerator

A tool to generate [Forge Version Checker](https://forge.gemwire.uk/wiki/Version_Checker) jsons based on the data on [CurseForge](https://www.curseforge.com/minecraft/mc-mods) or [Modrinth](https://modrinth.com/mods).

It accepts the following options:

  * `-p`, `--platform`: Either `curse` or `modrinth`.
  * `-c`, `--config`: A file containing project ids to generate version checker jsons for. One project id per line. Comments start with `#`
  * `-d`, `--dir`, `--directory`: The output directory to generate the version checker in.
  * `-f`, `--cache`: A cache file to store changelogs and version data from files, to reduce amount of requests to the API.

The version of a file is discovered by downloading it and parsing its contents. CfUpdateChecker looks for version information in this order:

  * A `META-INF/mods.toml` file that contains exactly one mod and a version value that does not start with a dollar sign.
  * A `mcmod.info` file that contains exactly one mod and a version value that does not start with a dollar sign.
  * A `META-INF/MANIFEST.MF` file with the property `Implementation-Version`
  * A `module-info.class` file that defines a module that has a version value set.