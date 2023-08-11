# Corn

[![](https://jitpack.io/v/rakjarz/corn.svg)](https://jitpack.io/#rakjarz/corn)

## Overview
An efficient utility library designed to assist you in logging to `Files`. It offers support for log rotation, prioritizes speed, and minimizes memory consumption. 

<p align="center">
	<kbd>
		<img src="assets/demo.gif" alt="Device Logger" width="480" height="985">
	</kbd>
</p>

## Key Features
- Fast write log to file 
- Auto log rotation
- Get logs pagination
- Clean log files
- Generate file reports

## Log Format
Following the linux `syslog` format with log `level` for debugging purpose.

```text
Time    Tag Level   Message   
```

## Usage
The library available in [jitpack.io](https://jitpack.io) repository. You can get it using:
```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.rakjarz:corn:1.0.2"
}
```

*Note*: The library requires minimum API level 21.

### Add to your app
Basic initialize
```kotlin
class App: Application {
    override fun onCreate() {
        // other code
        Corn.initialize(this)
    }

    override fun onTerminate() {
        // other code
        Corn.cleanup()
    }
}
```

Custom initialize 

```kotlin
Corn.initialize(
    context = this,
    retentionMillis = TimeUnit.DAYS.toMillis(90),
    logFormat = DefaultLogFormat()
)
```

### Logging
```kotlin
Corn.log(Level.DEBUG, tag = "MainActivity", message, throwable)
```

### Integrate with Timber
```kotlin
inner class FileTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Corn.log(priority, tag ?: "App", message, t)
    }
}

Timber.plant(FileTree())
```

### Get Logs
Default usage
```kotlin
val logs = Corn.getLogsAsStringList(offset=0, limit=200)
```

Some case you want to see the latest logs, so you have to flush the logs before calling get log function
*Note*: Corn flush callback will be called from background thread, so if you need to interact with UI, you need use `runOnUiThread`

```kotlin
 Corn.flush {
    val logs = Corn.getLogsAsStringList()
}
```

### Clear logs

If you just want to clean current log file, not the compressed back-up files, pass `cleanEveryThing=false`
Default is `true`

```kotlin
val cleanEverything = true
Corn.clear(cleanEverything)
```

## Developed By
- [Vu Phan](https://github.com/vuptt)

