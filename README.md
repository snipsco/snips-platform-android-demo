# Snips Android Demo
A demo of the Snips Platform for Android

This shows basic integration of the snips platform in an Android application. 

## Compiling 

A simple 
```
$ ./gradlew installDebug
```
should install the demo on a connected device

> If you see errors while connecting to `nexus.snips.ai` upgrade you java to a recent version (we're using Let's Encrypt for our SSL certificates and older version of java do not support it)

## Running

Make sure you have an unzipped snips assitant in the folder `snips_android_assistant` at the root of the external storage on you Android. You can create and download assistants on the [Snips Console](https://console.snips.ai)

Click the `start` button in the app to start the `snips-platform`.

Current implementation is completly dumb and show the detected hotword/intents. A simple TTS is said when an intent is detected.

## Going further

You can check the [installation](https://snips.gitbook.io/documentation/installing-snips/on-android) and [usage](https://snips.gitbook.io/documentation/installing-snips/on-android/using-the-platform-on-android) page in our documentation.

## Licence 

Licenced under the Apache License, Version 2.0 ([LICENSE](./LICENCE) or http://www.apache.org/licenses/LICENSE-2.0)
