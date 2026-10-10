/// The instance: what one is, where its directory is, and how it is created, renamed, copied and
/// removed.
///
/// The folders an instance may live in arrive from the launcher rather than being read here —
/// [DshInstanceManager#setFolders] is what points this package at them, and what a test uses in
/// place of a settings file.
package com.mcxcc303.hdsl.instance;