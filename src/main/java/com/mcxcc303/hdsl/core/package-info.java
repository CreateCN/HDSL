/// The mechanical parts every other package uses: the domain's one exception type, the paths the
/// launcher owns, removing a tree of files, running a command, and the YAML scalar rule.
///
/// Nothing here knows what an instance or a plugin is, which is why this is the one package the
/// others may depend on freely.
package com.mcxcc303.hdsl.core;