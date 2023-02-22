# play

A simple Android Java App Playground.

Usage:
```
java -jar play.jar <java-file>
```

Example:
```
java -jar play.jar HelloWorld.java
```

This tool:
1. Starts an app compiled from given one `<java-file>`
2. Restarts the app whenever the `<java-file>` is modified
3. Shows relevant information from `logcat`

All is done in a single java file with less than 500 LoC.

Requirements:
- JRE 7+
- An android device or emulator
