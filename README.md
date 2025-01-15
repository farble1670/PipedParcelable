# Piped Parcelable
A `Parcelable` implementation that wraps another `Parcelable`, using pipes to write and read the parcel data between processes. 

Android has a fixed limit of 1MB for binder transactions. This means in theory, any parcel must be less than this. In practice, errors can occur with much smaller parcels if an application is busy with IPC binder communication. Brace yourself for the dreaded `FAILED BINDER TRANSACTION`.
```
E/JavaBinder: !!! FAILED BINDER TRANSACTION !!!  (parcel size = 1085992)
D/AndroidRuntime: Shutting down VM
E/AndroidRuntime: FATAL EXCEPTION: main
              Process: com.example.myApp, PID: 1234
              java.lang.RuntimeException: android.os.TransactionTooLargeException: data parcel size 1085992 bytes
                  at android.app.ActivityThread$StopInfo.run(ActivityThread.java:3950)
                  at android.os.Handler.handleCallback(Handler.java:790)
                  at android.os.Handler.dispatchMessage(Handler.java:99)
                  at android.os.Looper.loop(Looper.java:164)
                  at android.app.ActivityThread.main(ActivityThread.java:6494)
                  at java.lang.reflect.Method.invoke(Native Method)
                  at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:438)
                  at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807)
               Caused by: android.os.TransactionTooLargeException: data parcel size 1085992 bytes
```
 `PipedParcelable` gets around the binder limit by transmitting the parcel data over a pipe. The only thing that's sent over the binder is a file descriptor: the read end of a pipe (typically 4 bytes, plus some overhead for Android's wrapper). For example:
```
val my = MyParcelable(bigData)
val piped = PipedParcelable(my)
val intent = Intent(...).apply {
	putExtra("my_parcelable", piped)
}
startActivity(intent) // Or #startService, broadcast, whatever
```
When the Android system marshals `piped` (calls `#writeToParcel`), `PipedParcelable` writes a file descriptor to the parcel, then starts a worker thread that writes the bytes of `my` to the write end of the pipe. 

On the receiver:
```
val my = intent.getParcelableExtra("my_parcelable").value
```
When the Android system unmarshals `piped`, `PipedParcelable` retrieves the read end of the pipe (a file descriptor) from the `Parcel`, and stores the bytes. When the caller invokes `#value` the bytes are used to reconstrcut the `MyParcelable` instance (lazily).

This can be used anywhere in Android where `Parcelable`s are transmitted over a binder channel (start activity / service, content provider call, broadcast, AIDL RPC, etc).

## Test
This project contains a few integration tests (in the `lib` module), and two test apps: `app1` and `app2`. These apps each have a content provider that when triggered, repeatedly send `PipedParcelable`s between each other with random payloads up to 10MB. To install both apps:
```
./gradlew installDebug
```
To start the test:
```
adb shell content call --uri content://org.jtb.piped_parcelable.app1.provider/test --method start && \
adb shell content call --uri content://org.jtb.piped_parcelable.app2.provider/test --method start
```
To observe the test:
```
adb logcat -v threadtime | grep "app[12].provider"
```
To stop the test:
```
adb shell content call --uri content://org.jtb.piped_parcelable.app1.provider/test --method stop && \
adb shell content call --uri content://org.jtb.piped_parcelable.app2.provider/test --method stop
```

## Threading
`PipedParcelable` is not thread safe. Construct them locally to wrap outgoing on incoming parcels, then throw them away (see example above).

Parcels are marshaled on a background thread. That means you won't be blocking wherever Android decides to call `#writeToParcel` on your `PipedParcelable`. **Unmarshaling happens on the calling thread**, e.g., when you call `Intent#getParcelableExtra`, and subsequently, `#value`. In practice, unmarshaling takes roughly 300ms for ~10 MB parcel on a medium-low end mobile processor (YMMV). If you're passing large parcels (which you are, or you wouldn't be using `PipedParcelable`) you should consider receiving them on a worker thread. For example:
```
lifecycleScope.launch {
  val my = withContext(Dispatchers.IO) {
    intent.getParcelableExtra("my_parcelable").value
  }
  ...
}
```
## Limits
The only limit should be memory available to store the wrapped `Parcelable` in the sender and receiver process.