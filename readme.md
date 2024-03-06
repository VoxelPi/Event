# Event

[![GitHub CI Status](https://img.shields.io/github/actions/workflow/status/voxelpi/event/ci.yml?branch=main&label=CI&style=for-the-badge)]()
[![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/net.voxelpi.event/event?server=https%3A%2F%2Frepo.voxelpi.net&nexusVersion=3&style=for-the-badge&label=stable&color=blue)]()
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/net.voxelpi.event/event?server=https%3A%2F%2Frepo.voxelpi.net&nexusVersion=3&style=for-the-badge&label=dev)]()

A simple event bus.

## Getting Started

```kotlin
repositories {
    maven {
        url = uri("https://repo.voxelpi.net/repository/maven-public/")
    }
}

dependencies {
    implementation("net.voxelpi.event:event:<version>")
}
```

## Examples

### Creating an event scope

Event scopes are the fundamental building block of Event.
They are used to register subscribers and post events.

```kotlin
// Create the event scope.
val scope = eventScope()

// Add callback that listens whenever a string is posted.
scope.on<String> {
    // Handle the event
}

// Post event.
scope.post("Hello, World!")

```

### Creating a sub event scope

Sub scopes can be used to help structure the event bus.

```kotlin
// Create the event scopes.
val scope = eventScope()
val subScope = scope.subScope()

// Register callbacks.
scope.on<String> {
    // Handle the event
}
subScope.on<String> {
    // Handle the event
}

// Post event.
scope.post("Handled by both handlers.")
subScope.post("Only handled by sub handler.")

```

### Using annotations

Event allows to automatically generate event subscriptions from a class
using the `net.voxelpi.event.annotation.Subscribe` annotation.

```kotlin
import net.voxelpi.event.annotation.Subscribe

// Create the event scope.
val scope = eventScope()

// Define class.
class HandlerClass {
    
    @Subscribe
    fun handle(event: String) {
        // Handle the event.
    }
}

// Register subscriptions.
val subscriptions = HandlerClass()
scope.registerAnnotated(subscriptions)

// Post event.
scope.post("Annotations!")

```
