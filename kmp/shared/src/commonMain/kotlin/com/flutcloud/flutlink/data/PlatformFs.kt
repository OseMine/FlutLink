package com.flutcloud.flutlink.data

import okio.FileSystem

/**
 * The platform's default file system, used by [WebDavApi] streaming helpers.
 * `FileSystem.SYSTEM` is JVM/native-only in okio, so common code goes through
 * this accessor instead.
 */
internal expect fun systemFileSystem(): FileSystem
