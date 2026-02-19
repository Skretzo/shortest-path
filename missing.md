Initialized native services in: C:\Users\woute\.gradle\native
Initialized jansi services in: C:\Users\woute\.gradle\native
Received JVM installation metadata from 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot': {JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot, JAVA_VERSION=17.0.17, JAVA_VENDOR=Eclipse Adoptium, RUNTIME_NAME=OpenJDK Runtime Environment, RUNTIME_VERSION=17.0.17+10, VM_NAME=OpenJDK 64-Bit Server VM, VM_VERSION=17.0.17+10, VM_VENDOR=Eclipse Adoptium, OS_ARCH=amd64}
Found daemon DaemonInfo{pid=793228, address=[c20a581f-436d-4310-b53c-4e6f9bd70701 port:49341, addresses:[/127.0.0.1]], state=Idle, lastBusy=1769634495203, context=DefaultDaemonContext[uid=cf7a66de-5eea-4c5f-8dd8-f892e30df3e0,javaHome=C:\Users\woute\.jdk\jdk-11.0.28,javaVersion=11,javaVendor=Microsoft,daemonRegistryDir=C:\Users\woute\.gradle\daemon,pid=793228,idleTimeout=10800000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=ENABLED,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,--add-opens=java.base/java.lang=ALL-UNNAMED,--add-opens=java.base/java.lang.invoke=ALL-UNNAMED,--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED,--add-opens=java.base/java.nio.charset=ALL-UNNAMED,--add-opens=java.base/java.net=ALL-UNNAMED,--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED,-XX:MaxMetaspaceSize=384m,-XX:+HeapDumpOnOutOfMemoryError,-Xms256m,-Xmx512m,-Dfile.encoding=windows-1252,-Duser.country=GB,-Duser.language=en,-Duser.variant]} however its context does not match the desired criteria.
JVM is incompatible.
Wanted: DaemonRequestContext{jvmCriteria=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot (no JDK specified, using current Java home), daemonOpts=[--add-opens=java.base/java.util=ALL-UNNAMED, --add-opens=java.base/java.lang=ALL-UNNAMED, --add-opens=java.base/java.lang.invoke=ALL-UNNAMED, --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED, --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED, --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED, --add-opens=java.base/java.nio.charset=ALL-UNNAMED, --add-opens=java.base/java.net=ALL-UNNAMED, --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED, -XX:MaxMetaspaceSize=384m, -XX:+HeapDumpOnOutOfMemoryError, -Xms256m, -Xmx512m, -Dfile.encoding=windows-1252, -Duser.country=GB, -Duser.language=en, -Duser.variant], applyInstrumentationAgent=true, nativeServicesMode=ENABLED, priority=NORMAL}
Actual: DefaultDaemonContext[uid=cf7a66de-5eea-4c5f-8dd8-f892e30df3e0,javaHome=C:\Users\woute\.jdk\jdk-11.0.28,javaVersion=11,javaVendor=Microsoft,daemonRegistryDir=C:\Users\woute\.gradle\daemon,pid=793228,idleTimeout=10800000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=ENABLED,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,--add-opens=java.base/java.lang=ALL-UNNAMED,--add-opens=java.base/java.lang.invoke=ALL-UNNAMED,--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED,--add-opens=java.base/java.nio.charset=ALL-UNNAMED,--add-opens=java.base/java.net=ALL-UNNAMED,--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED,-XX:MaxMetaspaceSize=384m,-XX:+HeapDumpOnOutOfMemoryError,-Xms256m,-Xmx512m,-Dfile.encoding=windows-1252,-Duser.country=GB,-Duser.language=en,-Duser.variant]

  Looking for a different daemon...
Found daemon DaemonInfo{pid=175788, address=[79d39f20-2445-4f02-a4f5-6f674dae3555 port:18840, addresses:[/127.0.0.1]], state=Idle, lastBusy=1770150814717, context=DefaultDaemonContext[uid=d82e5333-06bd-4275-a97b-d0b3c2ddd9a8,javaHome=C:\Users\woute\.vscode\extensions\redhat.java-1.51.0-win32-x64\jre\21.0.9-win32-x86_64,javaVersion=21,javaVendor=Eclipse Adoptium,daemonRegistryDir=C:\Users\woute\.gradle\daemon,pid=175788,idleTimeout=10800000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=ENABLED,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,--add-opens=java.base/java.lang=ALL-UNNAMED,--add-opens=java.base/java.lang.invoke=ALL-UNNAMED,--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED,--add-opens=java.base/java.nio.charset=ALL-UNNAMED,--add-opens=java.base/java.net=ALL-UNNAMED,--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED,-XX:MaxMetaspaceSize=384m,-XX:+HeapDumpOnOutOfMemoryError,-Xms256m,-Xmx512m,-Dfile.encoding=UTF-8,-Duser.country=GB,-Duser.language=en,-Duser.variant]} however its context does not match the desired criteria.
JVM is incompatible.
Wanted: DaemonRequestContext{jvmCriteria=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot (no JDK specified, using current Java home), daemonOpts=[--add-opens=java.base/java.util=ALL-UNNAMED, --add-opens=java.base/java.lang=ALL-UNNAMED, --add-opens=java.base/java.lang.invoke=ALL-UNNAMED, --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED, --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED, --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED, --add-opens=java.base/java.nio.charset=ALL-UNNAMED, --add-opens=java.base/java.net=ALL-UNNAMED, --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED, -XX:MaxMetaspaceSize=384m, -XX:+HeapDumpOnOutOfMemoryError, -Xms256m, -Xmx512m, -Dfile.encoding=windows-1252, -Duser.country=GB, -Duser.language=en, -Duser.variant], applyInstrumentationAgent=true, nativeServicesMode=ENABLED, priority=NORMAL}
Actual: DefaultDaemonContext[uid=d82e5333-06bd-4275-a97b-d0b3c2ddd9a8,javaHome=C:\Users\woute\.vscode\extensions\redhat.java-1.51.0-win32-x64\jre\21.0.9-win32-x86_64,javaVersion=21,javaVendor=Eclipse Adoptium,daemonRegistryDir=C:\Users\woute\.gradle\daemon,pid=175788,idleTimeout=10800000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=ENABLED,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,--add-opens=java.base/java.lang=ALL-UNNAMED,--add-opens=java.base/java.lang.invoke=ALL-UNNAMED,--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED,--add-opens=java.base/java.nio.charset=ALL-UNNAMED,--add-opens=java.base/java.net=ALL-UNNAMED,--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED,-XX:MaxMetaspaceSize=384m,-XX:+HeapDumpOnOutOfMemoryError,-Xms256m,-Xmx512m,-Dfile.encoding=UTF-8,-Duser.country=GB,-Duser.language=en,-Duser.variant]

  Looking for a different daemon...
Found daemon DaemonInfo{pid=442808, address=[488a3bb0-e23c-458c-8c79-d6fded93536d port:53567, addresses:[/127.0.0.1]], state=Idle, lastBusy=1771159211356, context=DefaultDaemonContext[uid=94683a7c-3959-40b6-9afa-52815436ada1,javaHome=C:\Users\woute\.vscode\extensions\redhat.java-1.52.0-win32-x64\jre\21.0.9-win32-x86_64,javaVersion=21,javaVendor=Eclipse Adoptium,daemonRegistryDir=C:\Users\woute\.gradle\daemon,pid=442808,idleTimeout=10800000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=ENABLED,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,--add-opens=java.base/java.lang=ALL-UNNAMED,--add-opens=java.base/java.lang.invoke=ALL-UNNAMED,--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED,--add-opens=java.base/java.nio.charset=ALL-UNNAMED,--add-opens=java.base/java.net=ALL-UNNAMED,--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED,-XX:MaxMetaspaceSize=384m,-XX:+HeapDumpOnOutOfMemoryError,-Xms256m,-Xmx512m,-Dfile.encoding=UTF-8,-Duser.country=GB,-Duser.language=en,-Duser.variant]} however its context does not match the desired criteria.
JVM is incompatible.
Wanted: DaemonRequestContext{jvmCriteria=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot (no JDK specified, using current Java home), daemonOpts=[--add-opens=java.base/java.util=ALL-UNNAMED, --add-opens=java.base/java.lang=ALL-UNNAMED, --add-opens=java.base/java.lang.invoke=ALL-UNNAMED, --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED, --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED, --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED, --add-opens=java.base/java.nio.charset=ALL-UNNAMED, --add-opens=java.base/java.net=ALL-UNNAMED, --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED, -XX:MaxMetaspaceSize=384m, -XX:+HeapDumpOnOutOfMemoryError, -Xms256m, -Xmx512m, -Dfile.encoding=windows-1252, -Duser.country=GB, -Duser.language=en, -Duser.variant], applyInstrumentationAgent=true, nativeServicesMode=ENABLED, priority=NORMAL}
Actual: DefaultDaemonContext[uid=94683a7c-3959-40b6-9afa-52815436ada1,javaHome=C:\Users\woute\.vscode\extensions\redhat.java-1.52.0-win32-x64\jre\21.0.9-win32-x86_64,javaVersion=21,javaVendor=Eclipse Adoptium,daemonRegistryDir=C:\Users\woute\.gradle\daemon,pid=442808,idleTimeout=10800000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=ENABLED,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,--add-opens=java.base/java.lang=ALL-UNNAMED,--add-opens=java.base/java.lang.invoke=ALL-UNNAMED,--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED,--add-opens=java.base/java.nio.charset=ALL-UNNAMED,--add-opens=java.base/java.net=ALL-UNNAMED,--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED,-XX:MaxMetaspaceSize=384m,-XX:+HeapDumpOnOutOfMemoryError,-Xms256m,-Xmx512m,-Dfile.encoding=UTF-8,-Duser.country=GB,-Duser.language=en,-Duser.variant]

  Looking for a different daemon...
Found daemon DaemonInfo{pid=443180, address=[49272e1b-1600-4230-be81-e4f06efe9250 port:53569, addresses:[/127.0.0.1]], state=Idle, lastBusy=1771159243507, context=DefaultDaemonContext[uid=aec772b0-612d-4924-83ff-f168622037ca,javaHome=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot,javaVersion=17,javaVendor=Eclipse Adoptium,daemonRegistryDir=C:\Users\woute\.gradle\daemon,pid=443180,idleTimeout=10800000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=ENABLED,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,--add-opens=java.base/java.lang=ALL-UNNAMED,--add-opens=java.base/java.lang.invoke=ALL-UNNAMED,--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED,--add-opens=java.base/java.nio.charset=ALL-UNNAMED,--add-opens=java.base/java.net=ALL-UNNAMED,--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED,-XX:MaxMetaspaceSize=384m,-XX:+HeapDumpOnOutOfMemoryError,-Xms256m,-Xmx512m,-Dfile.encoding=utf8,-Duser.country=GB,-Duser.language=en,-Duser.variant]} however its context does not match the desired criteria.
At least one daemon option is different.
Wanted: DaemonRequestContext{jvmCriteria=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot (no JDK specified, using current Java home), daemonOpts=[--add-opens=java.base/java.util=ALL-UNNAMED, --add-opens=java.base/java.lang=ALL-UNNAMED, --add-opens=java.base/java.lang.invoke=ALL-UNNAMED, --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED, --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED, --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED, --add-opens=java.base/java.nio.charset=ALL-UNNAMED, --add-opens=java.base/java.net=ALL-UNNAMED, --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED, -XX:MaxMetaspaceSize=384m, -XX:+HeapDumpOnOutOfMemoryError, -Xms256m, -Xmx512m, -Dfile.encoding=windows-1252, -Duser.country=GB, -Duser.language=en, -Duser.variant], applyInstrumentationAgent=true, nativeServicesMode=ENABLED, priority=NORMAL}
Actual: DefaultDaemonContext[uid=aec772b0-612d-4924-83ff-f168622037ca,javaHome=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot,javaVersion=17,javaVendor=Eclipse Adoptium,daemonRegistryDir=C:\Users\woute\.gradle\daemon,pid=443180,idleTimeout=10800000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=ENABLED,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,--add-opens=java.base/java.lang=ALL-UNNAMED,--add-opens=java.base/java.lang.invoke=ALL-UNNAMED,--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED,--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED,--add-opens=java.base/java.nio.charset=ALL-UNNAMED,--add-opens=java.base/java.net=ALL-UNNAMED,--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED,-XX:MaxMetaspaceSize=384m,-XX:+HeapDumpOnOutOfMemoryError,-Xms256m,-Xmx512m,-Dfile.encoding=utf8,-Duser.country=GB,-Duser.language=en,-Duser.variant]

  Looking for a different daemon...
The client will now receive all logging from the daemon (pid: 418560). The daemon log file: C:\Users\woute\.gradle\daemon\8.10\daemon-418560.out.log
Starting 2nd build in daemon [uptime: 6 mins 50.647 secs, performance: 100%, GC rate: 0.00/s, heap usage: 0% of 512 MiB, non-heap usage: 14% of 384 MiB]
Using 16 worker leases.
Now considering [C:\Users\woute\Code\fork\shortest-path] as hierarchies to watch
Watching the file system is configured to be enabled if available
File system watching is active
Starting Build
Settings evaluated using settings file 'C:\Users\woute\Code\fork\shortest-path\settings.gradle'.
Projects loaded. Root project using build file 'C:\Users\woute\Code\fork\shortest-path\build.gradle'.
Included projects: [root project 'shortest-path']

> Configure project :
Evaluating root project 'shortest-path' using build file 'C:\Users\woute\Code\fork\shortest-path\build.gradle'.
All projects evaluated.
Task name matched 'test'
Selected primary task 'test' from project :
Tasks to be executed: [task ':compileJava', task ':processResources', task ':classes', task ':compileTestJava', task ':processTestResources', task ':testClasses', task ':test']
Tasks that were excluded: []
Resolve mutations for :compileJava (Thread[Execution worker,5,main]) started.
:compileJava (Thread[Execution worker Thread 14,5,main]) started.

> Task :compileJava UP-TO-DATE
Caching disabled for task ':compileJava' because:
  Build cache is disabled
Skipping task ':compileJava' as it is up-to-date.
Resolve mutations for :processResources (Thread[Execution worker Thread 14,5,main]) started.
:processResources (Thread[Execution worker Thread 14,5,main]) started.

> Task :processResources UP-TO-DATE
Caching disabled for task ':processResources' because:
  Build cache is disabled
  Not worth caching
Skipping task ':processResources' as it is up-to-date.
Resolve mutations for :classes (Thread[Execution worker Thread 14,5,main]) started.
:classes (Thread[Execution worker Thread 14,5,main]) started.

> Task :classes UP-TO-DATE
Skipping task ':classes' as it has no actions.
Resolve mutations for :compileTestJava (Thread[Execution worker Thread 9,5,main]) started.
:compileTestJava (Thread[Execution worker Thread 9,5,main]) started.

> Task :compileTestJava
Caching disabled for task ':compileTestJava' because:
  Build cache is disabled
Task ':compileTestJava' is not up-to-date because:
  Input property 'stableSources' file C:\Users\woute\Code\fork\shortest-path\src\test\java\shortestpath\AgilityShortcutTest.java has changed.
Compilation mode: in-process compilation
Created classpath snapshot for incremental compilation in 0.069 secs.
Compiling with toolchain 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'.
Compiling with JDK Java compiler API.
Incremental compilation of 2 classes completed in 0.84 secs.
Class dependency analysis for incremental compilation took 0.014 secs.
Resolve mutations for :processTestResources (Thread[Execution worker Thread 9,5,main]) started.
:processTestResources (Thread[Execution worker Thread 9,5,main]) started.

> Task :processTestResources NO-SOURCE
Skipping task ':processTestResources' as it has no source files and no previous output files.
Resolve mutations for :testClasses (Thread[Execution worker Thread 9,5,main]) started.
:testClasses (Thread[Execution worker Thread 9,5,main]) started.

> Task :testClasses
Skipping task ':testClasses' as it has no actions.
Resolve mutations for :test (Thread[Execution worker Thread 9,5,main]) started.
:test (Thread[Execution worker Thread 9,5,main]) started.
Gradle Test Executor 2 started executing tests.

> Task :test
Custom actions are attached to task ':test'.
Caching disabled for task ':test' because:
  Build cache is disabled
Task ':test' is not up-to-date because:
  Task has failed previously.
Starting process 'Gradle Test Executor 2'. Working directory: C:\Users\woute\Code\fork\shortest-path Command: C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot\bin\java.exe -Dorg.gradle.internal.worker.tmpdir=C:\Users\woute\Code\fork\shortest-path\build\tmp\test\work -javaagent:C:\Users\woute\Code\fork\shortest-path\build\tmp\.cache\expanded\zip_cde35f471dab581134460fc9a50e2c59\jacocoagent.jar=destfile=build/jacoco/test.exec,append=true,inclnolocationclasses=false,dumponexit=true,output=file,jmx=false @C:\Users\woute\.gradle\.tmp\gradle-worker-classpath16672253373284063751txt -Xms1g -Xmx8g -Dfile.encoding=windows-1252 -Duser.country=GB -Duser.language=en -Duser.variant -ea worker.org.gradle.process.internal.worker.GradleWorkerMain 'Gradle Test Executor 2'
Successfully started process 'Gradle Test Executor 2'

AgilityShortcutTest > everyEnumHasTsvEntry STANDARD_OUT
    Skipping excluded ID 46815 for WEISS_BROKEN_FENCE
    Skipping excluded ID 11948 for AGILITY_PYRAMID_ROCKS_WEST
    Skipping excluded ID 23644 for KARAMJA_GLIDER_LOG
    Skipping excluded ID 23568 for KARAMJA_MOSS_GIANT_SWING
    Skipping excluded ID 23569 for KARAMJA_MOSS_GIANT_SWING
    Skipping excluded ID 31697 for CRABCLAW_CAVES_ROCKS
    Missing TSV entries for the following AgilityShortcut enum constants:
    DEEP_WILDERNESS_DUNGEON_CREVICE_NORTH - 46 - WorldPoint(x=3047, y=10335, plane=0)
    DEEP_WILDERNESS_DUNGEON_CREVICE_SOUTH - 46 - WorldPoint(x=3045, y=10327, plane=0)
    TONALI_CAVERN_STEPPING_STONE - 46 - null
    TONALI_CAVERN_LOG_BALANCE - 46 - null
    GREAT_CONCH_CLIFF_SHORTCUT_TOWN - 50 - WorldPoint(x=3180, y=2433, plane=0)
    ANGLERS_RETREAT_SHORTCUT - 52 - WorldPoint(x=2475, y=2729, plane=0)
    GREAT_CONCH_CLIFF_SHORTCUT_EAST_1 - 52 - WorldPoint(x=3235, y=2388, plane=0)
    MOTHERLODE_MINE_WALL_EAST - 54 - WorldPoint(x=3124, y=9703, plane=0)
    MOTHERLODE_MINE_WALL_WEST - 54 - WorldPoint(x=3118, y=9702, plane=0)
    GREAT_CONCH_CLIFF_SHORTCUT_SOUTHEAST_BOTTOM - 55 - WorldPoint(x=3272, y=2330, plane=0)
    GREAT_CONCH_CLIFF_SHORTCUT_EAST_2 - 57 - WorldPoint(x=3256, y=2397, plane=0)
    ASGARNIA_ICE_DUNGEON_TUNNEL_WEST - 60 - WorldPoint(x=2968, y=9549, plane=0)
    GREAT_CONCH_STEPPING_STONE - 61 - WorldPoint(x=3208, y=2394, plane=0)
    LAGUNA_AURORAE_SHORTCUT_1 - 61 - WorldPoint(x=1152, y=2804, plane=0)
    LAGUNA_AURORAE_SHORTCUT_2 - 61 - WorldPoint(x=1142, y=2803, plane=0)
    HEROES_GUILD_TUNNEL_EAST - 67 - WorldPoint(x=2898, y=9901, plane=0)
    HEROES_GUILD_TUNNEL_WEST - 67 - WorldPoint(x=2913, y=9895, plane=0)
    FENKENSTRAIN_MAUSOLEUM_BRIDGE - 69 - WorldPoint(x=3504, y=3560, plane=0)
    GU_TANOTH_CRUMBLING_WALL - 71 - WorldPoint(x=2545, y=3032, plane=0)
    ASGARNIA_ICE_DUNGEON_BASIC_SOUTH - 72 - WorldPoint(x=3033, y=9559, plane=0)
    BARROWS_WALL_JUMP - 72 - WorldPoint(x=3544, y=3282, plane=0)
    COF_PLATFORM_TOP - 73 - WorldPoint(x=1438, y=10098, plane=2)
    COF_PLATFORM_MID - 73 - WorldPoint(x=1443, y=10097, plane=1)
    MEIYERDITCH_LAB_TUNNELS_NORTH - 74 - WorldPoint(x=3623, y=9747, plane=0)
    MEIYERDITCH_LAB_TUNNELS_SOUTH - 74 - WorldPoint(x=3618, y=9722, plane=0)
    FOSSIL_ISLAND_ZIPLINE - 74 - WorldPoint(x=3764, y=3883, plane=0)
    MOKHAIOTL_PIT_JUMP - 75 - null
    DARKFROST_CLIFF_SCRAMBLE - 76 - WorldPoint(x=1477, y=3307, plane=0)
    WILDERNESS_SLAYER_CAVE_CREVICE_NORTH_EAST - 77 - WorldPoint(x=3433, y=10093, plane=0)
    WILDERNESS_SLAYER_CAVE_CREVICE_SOUTH_EAST - 77 - WorldPoint(x=3434, y=10115, plane=0)
    WILDERNESS_SLAYER_CAVE_CREVICE_NORTH_WEST - 77 - WorldPoint(x=3341, y=10149, plane=0)
    WILDERNESS_SLAYER_CAVE_CREVICE_SOUTH_WEST - 77 - WorldPoint(x=3333, y=10119, plane=0)
    IORWERTHS_DUNGEON_NORTHERN_SHORTCUT_EAST - 78 - WorldPoint(x=3221, y=12441, plane=0)
    IORWERTHS_DUNGEON_NORTHERN_SHORTCUT_WEST - 78 - WorldPoint(x=3215, y=12441, plane=0)
    KHARAZI_JUNGLE_VINE_CLIMB - 79 - WorldPoint(x=2897, y=2939, plane=0)
    SLAYER_TOWER_IVY - 81 - WorldPoint(x=3417, y=3533, plane=0)
    SLAYER_TOWER_TOP_WINDOW - 81 - WorldPoint(x=3419, y=3534, plane=0)
    WATERBIRTH_DUNGEON_CREVICE - 81 - WorldPoint(x=2604, y=10070, plane=0)
    ASGARNIA_ICE_DUNGEON_ADEPT_EAST - 82 - WorldPoint(x=3022, y=9553, plane=0)
    COF_SHORTCUT_TOP - 83 - WorldPoint(x=1436, y=10075, plane=2)
    GRIMSTONE_SHORTCUT_SOUTH - 83 - WorldPoint(x=2901, y=10454, plane=0)
    IORWERTHS_DUNGEON_SOUTHERN_SHORTCUT_EAST - 84 - WorldPoint(x=3241, y=12420, plane=0)
    IORWERTHS_DUNGEON_SOUTHERN_SHORTCUT_WEST - 84 - WorldPoint(x=3231, y=12420, plane=0)
    DEEPFIN_CAVE_SHORTCUT - 84 - WorldPoint(x=2081, y=9203, plane=0)
    DEEPFIN_CAVE_SHORTCUT_ICON - 84 - WorldPoint(x=2075, y=9203, plane=0)
    BRIMHAVEN_DUNGEON_VINE_EAST - 87 - WorldPoint(x=2672, y=9582, plane=0)
    BRIMHAVEN_DUNGEON_VINE_WEST - 87 - WorldPoint(x=2606, y=9584, plane=0)
    MOUNT_KARUULM_PIPE_SOUTH - 88 - WorldPoint(x=1316, y=10214, plane=0)
    MOUNT_KARUULM_PIPE_NORTH - 88 - WorldPoint(x=1345, y=10230, plane=0)
    VIYELDI_ROCK_CLIMB - 91 - null
    MEIYERDITCH_LAB_ADVANCED_TUNNELS_WEST - 93 - WorldPoint(x=3499, y=9738, plane=0)
    MEIYERDITCH_LAB_ADVANCED_TUNNELS_MIDDLE - 93 - WorldPoint(x=3597, y=9704, plane=0)
    MEIYERDITCH_LAB_ADVANCED_TUNNELS_EAST - 93 - WorldPoint(x=3604, y=9708, plane=0)

AgilityShortcutTest > everyEnumHasTsvEntry FAILED
    java.lang.AssertionError: Missing TSV entries for AgilityShortcut enum constants (see stdout for list)
        at org.junit.Assert.fail(Assert.java:89)
        at shortestpath.AgilityShortcutTest.everyEnumHasTsvEntry(AgilityShortcutTest.java:71)

PathfinderTest > testTeleportationLevers STANDARD_OUT
    Successfully completed 7 TELEPORTATION_LEVER transport length tests

PathfinderTest > testTeleportationPortals STANDARD_OUT
    Successfully completed 104 TELEPORTATION_PORTAL transport length tests

PathfinderTest > testImpossibleCharterShips STANDARD_OUT
    Successfully completed transport length test from (1455, 2968, 0) to (1514, 2971, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (1514, 2971, 0) to (1455, 2968, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (3702, 3503, 0) to (3671, 2931, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (3671, 2931, 0) to (3702, 3503, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (1808, 3679, 0) to (1496, 3403, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (1496, 3403, 0) to (1808, 3679, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (3038, 3192, 0) to (1496, 3403, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (1496, 3403, 0) to (3038, 3192, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (3038, 3192, 0) to (2954, 3158, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (2954, 3158, 0) to (3038, 3192, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (3038, 3192, 0) to (1808, 3679, 0) with actual length = 3 >= minimum length = 3
    Successfully completed transport length test from (1808, 3679, 0) to (3038, 3192, 0) with actual length = 3 >= minimum length = 3

PathfinderTest > testBoats STANDARD_OUT
    Successfully completed 111 BOAT transport length tests

PathfinderTest > testCaves STANDARD_OUT
    Successfully completed transport length test from (2892, 3671, 0) to (2893, 10074, 2)
    Successfully completed transport length test from (2893, 3671, 0) to (2893, 10074, 2)
    Successfully completed transport length test from (2894, 3671, 0) to (2893, 10074, 2)
    Successfully completed transport length test from (2895, 3672, 0) to (2893, 10074, 2)
    Successfully completed transport length test from (2892, 10074, 2) to (2893, 3671, 0)
    Successfully completed transport length test from (2893, 10074, 2) to (2893, 3671, 0)
    Successfully completed transport length test from (2894, 10074, 2) to (2893, 3671, 0)

PathfinderTest > testMagicCarpets STANDARD_OUT
    Successfully completed 13 MAGIC_CARPET transport length tests

PathfinderTest > testShips STANDARD_OUT
    Successfully completed 31 SHIP transport length tests

PathfinderTest > testAgilityShortcuts STANDARD_OUT
    Successfully completed 554 AGILITY_SHORTCUT transport length tests

PathfinderTest > testGnomeGliders STANDARD_OUT
    Successfully completed 90 GNOME_GLIDER transport length tests

PathfinderTest > testPathViaOtherPlane STANDARD_OUT
    Successfully completed transport length test from (2894, 10199, 0) to (2864, 10199, 0)
    Successfully completed transport length test from (2864, 10199, 0) to (2894, 10199, 0)

PathfinderTest > testFairyRingsUsedWithDramenStaffWornInHand FAILED
    java.lang.AssertionError: (2027, 5700, 0) to (2412, 4434, 0) expected:<2> but was:<272>
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.failNotEquals(Assert.java:835)
        at org.junit.Assert.assertEquals(Assert.java:647)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:548)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:536)
        at shortestpath.pathfinder.PathfinderTest.testFairyRingsUsedWithDramenStaffWornInHand(PathfinderTest.java:161)

PathfinderTest > testFairyRingsUsedWithLumbridgeDiaryCompleteWithoutDramenStaff FAILED
    java.lang.AssertionError: (2027, 5700, 0) to (2412, 4434, 0) expected:<2> but was:<272>
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.failNotEquals(Assert.java:835)
        at org.junit.Assert.assertEquals(Assert.java:647)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:548)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:536)
        at shortestpath.pathfinder.PathfinderTest.testFairyRingsUsedWithLumbridgeDiaryCompleteWithoutDramenStaff(PathfinderTest.java:150)

PathfinderTest > testQuetzals STANDARD_OUT
    Successfully completed 184 QUETZAL transport length tests

PathfinderTest > testTeleportationMinigames STANDARD_OUT
    Successfully completed transport length test from (3440, 3334, 0) to (2658, 3157, 0)
    Successfully completed transport length test from (3136, 3525, 0) to (2658, 3157, 0)

PathfinderTest > testHotAirBalloons STANDARD_OUT
    Successfully completed 225 HOT_AIR_BALLOON transport length tests

PathfinderTest > testCharterShips STANDARD_OUT
    Successfully completed 228 CHARTER_SHIP transport length tests

PathfinderTest > testGrappleShortcuts STANDARD_OUT
    Successfully completed 14 GRAPPLE_SHORTCUT transport length tests

PathfinderTest > testChronicle STANDARD_OUT
    Successfully completed transport length test from (3199, 3336, 0) to (3200, 3355, 0)

PathfinderTest > testWildernessObelisks FAILED
    java.lang.AssertionError: (1888, 5717, 0) to (3106, 3794, 0) expected:<2> but was:<176>
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.failNotEquals(Assert.java:835)
        at org.junit.Assert.assertEquals(Assert.java:647)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:548)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:536)
        at shortestpath.pathfinder.PathfinderTest.testWildernessObelisks(PathfinderTest.java:299)

PathfinderTest > testAgilityShortcutAndTeleportItem STANDARD_OUT
    Successfully completed transport length test from (3149, 3363, 0) to (3154, 3363, 0)

PathfinderTest > testSpiritTrees FAILED
    java.lang.AssertionError: (2007, 5700, 0) to (2488, 2850, 0) expected:<2> but was:<217>
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.failNotEquals(Assert.java:835)
        at org.junit.Assert.assertEquals(Assert.java:647)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:548)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:536)
        at shortestpath.pathfinder.PathfinderTest.testSpiritTrees(PathfinderTest.java:213)

PathfinderTest > testTeleportationBoxes FAILED
    java.lang.AssertionError: (1886, 5760, 0) to (1644, 3673, 0) expected:<2> but was:<191>
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.failNotEquals(Assert.java:835)
        at org.junit.Assert.assertEquals(Assert.java:647)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:548)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:536)
        at shortestpath.pathfinder.PathfinderTest.testTeleportationBoxes(PathfinderTest.java:221)

PathfinderTest > testCanoes STANDARD_OUT
    Successfully completed 25 CANOE transport length tests

PathfinderTest > testVarrockTeleport STANDARD_OUT
    Successfully completed transport length test from (3216, 3424, 0) to (3213, 3424, 0)
    Successfully completed transport length test from (3216, 3424, 0) to (3213, 3424, 0)

PathfinderTest > testMinecarts STANDARD_OUT
    Successfully completed 255 MINECART transport length tests

PathfinderTest > testFairyRings FAILED
    java.lang.AssertionError: (2027, 5700, 0) to (2412, 4434, 0) expected:<2> but was:<272>
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.failNotEquals(Assert.java:835)
        at org.junit.Assert.assertEquals(Assert.java:647)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:548)
        at shortestpath.pathfinder.PathfinderTest.testTransportLength(PathfinderTest.java:536)
        at shortestpath.pathfinder.PathfinderTest.testFairyRings(PathfinderTest.java:119)

PathfinderTest > testMagicMushtrees STANDARD_OUT
    Successfully completed 24 MAGIC_MUSHTREE transport length tests

TransportDataLintTest > testNoDuplicateOriginDestinationPairs FAILED
    java.lang.AssertionError: Found 4 duplicate transport entries in the data files:

    Exact duplicate transport: 1647 10010 0 -> 1645 10000 0 [AGILITY_SHORTCUT] '' Consumable:false Items:none Quests:[] Varbits:[] VarPlayers:[] ObjectInfo:Squeeze-through Crack 28892
    First occurrence: MaxWilderness: -1, Skills: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 17, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    Duplicate occurrence: MaxWilderness: -1, Skills: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 17, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]

    Exact duplicate transport: 1645 10000 0 -> 1647 10010 0 [AGILITY_SHORTCUT] '' Consumable:false Items:none Quests:[] Varbits:[] VarPlayers:[] ObjectInfo:Squeeze-through Crack 28892
    First occurrence: MaxWilderness: -1, Skills: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 17, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    Duplicate occurrence: MaxWilderness: -1, Skills: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 17, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]

    Exact duplicate transport: 1613 10070 0 -> 1609 10061 0 [AGILITY_SHORTCUT] '' Consumable:false Items:none Quests:[] Varbits:[] VarPlayers:[] ObjectInfo:Jump-to Stone 28893
    First occurrence: MaxWilderness: -1, Skills: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 28, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    Duplicate occurrence: MaxWilderness: -1, Skills: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 28, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]

    Exact duplicate transport: 1609 10061 0 -> 1613 10070 0 [AGILITY_SHORTCUT] '' Consumable:false Items:none Quests:[] Varbits:[] VarPlayers:[] ObjectInfo:Jump-to Stone 28893
    First occurrence: MaxWilderness: -1, Skills: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 28, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    Duplicate occurrence: MaxWilderness: -1, Skills: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 28, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
        at org.junit.Assert.fail(Assert.java:89)
        at shortestpath.transport.TransportDataLintTest.testNoDuplicateOriginDestinationPairs(TransportDataLintTest.java:98)

TransportLoaderTest > testMalformedData STANDARD_OUT
    2026-02-15 14:05:11 CET [Test worker] ERROR shortestpath.transport.Transport - Invalid level and skill: invalid skill
    2026-02-15 14:05:11 CET [Test worker] ERROR shortestpath.transport.Transport - Invalid item or quantity: INVALID=ITEMS
    2026-02-15 14:05:11 CET [Test worker] ERROR shortestpath.transport.Transport - Invalid tick duration: invalid_duration
    2026-02-15 14:05:11 CET [Test worker] ERROR shortestpath.transport.Transport - Invalid wilderness level: invalid_wilderness

Gradle Test Executor 2 finished executing tests.

> Task :test FAILED
Finished generating test XML results (0.009 secs) into: C:\Users\woute\Code\fork\shortest-path\build\test-results\test
Generating HTML test report...
Finished generating test html results (0.013 secs) into: C:\Users\woute\Code\fork\shortest-path\build\reports\tests\test

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.10/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
4 actionable tasks: 2 executed, 2 up-to-date
Watched directory hierarchies: [C:\Users\woute\Code\fork\shortest-path]
