package example.plugins

import java.io.Closeable

interface ClosableJob : Closeable, Runnable
