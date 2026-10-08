# AgonesSync

A [Paper](https://papermc.io) plugin that connects a Minecraft server to the [Agones](https://agones.dev) SDK sidecar.

It will:

- send **health pings** so Agones doesn't mark the server `Unhealthy`
- call **`Ready`** once the server has finished starting
- report the **player count** as a Counter (`players`) for Counter-based autoscaling
- optionally mark the server **`Allocated`** when full and switch back to **`Ready`** when a slot frees up
- call **`Shutdown`** when the server stops

## How it works

Agones injects an SDK sidecar into every GameServer pod. The plugin talks to it over the local REST API:

```
http://localhost:${AGONES_SDK_HTTP_PORT}   (default 9358)
```

| Event | SDK call |
|---|---|
| Plugin enabled (first server tick) | `POST /ready` |
| Every 2 s | `POST /health` |
| Player joins/quits | `PATCH /v1beta1/counters/players` |
| Server full (optional) | `POST /allocate` |
| Slot frees up (optional) | `POST /ready` |
| Plugin disabled | `POST /shutdown` |

## Requirements

- Java 25 (matches the build)
- Gradle 9.7.0+ (needed by `run-paper` 3.1.0, see [Build](#build))
- Paper server
- A Kubernetes cluster with Agones installed
- For Counters: Agones with the `CountsAndLists` feature enabled (verify against your Agones version's docs)

## Build

```bash
./gradlew build
```

The jar ends up in `build/libs/`.

If Gradle fails with `No matching variant of xyz.jpenilla:run-task`, your wrapper is too old. Set it in `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip
```

Test locally with a dev server:

```bash
./gradlew runServer
```

Without a sidecar the SDK calls simply fail and get logged. To test against a mock sidecar:

```bash
docker run -p 9358:9358 us-docker.pkg.dev/agones-images/release/agones-sdk:<version> --local
```

(`--local` may not implement the counter endpoints; test those on a real cluster.)

## Setup

### 1. Build the server image

```dockerfile
FROM itzg/minecraft-server:java25

# itzg copies /plugins into /data/plugins on startup
COPY build/libs/AgonesSync-*.jar /plugins/

ENV TYPE=PAPER \
    EULA=TRUE
```

Adjust the jar name to your build output, and the tag to a Java 25 image that exists for your setup.

```bash
./gradlew build
docker build -t registry.example.com/mc-paper:latest .
docker push registry.example.com/mc-paper:latest
```

### 2. Create the Fleet

```yaml
apiVersion: agones.dev/v1
kind: Fleet
metadata:
  name: mc-fleet
spec:
  replicas: 2
  template:
    spec:
      ports:
        - name: minecraft
          containerPort: 25565
          protocol: TCP
      health:
        initialDelaySeconds: 60   # Paper needs time to boot
        periodSeconds: 5
        failureThreshold: 3
      counters:
        players:
          count: 0
          capacity: 20
      template:
        spec:
          containers:
            - name: minecraft
              image: registry.example.com/mc-paper:latest
              resources:
                requests:
                  cpu: "1"
                  memory: 2Gi
                limits:
                  memory: 2Gi
```

`initialDelaySeconds` must be longer than your server's startup time, otherwise Agones can mark it `Unhealthy` before the plugin sends its first ping.

### 3. Choose a scaling strategy

Pick **one**. Mixing them makes them fight each other.

#### Option A: Counter autoscaler (recommended)

The plugin reports the player count; Agones keeps a buffer of free slots. Servers never change state because of player counts.

```yaml
apiVersion: autoscaling.agones.dev/v1
kind: FleetAutoscaler
metadata:
  name: mc-autoscaler
spec:
  fleetName: mc-fleet
  policy:
    type: Counter
    counter:
      key: players
      bufferSize: 10      # keep 10 free player slots across Ready servers
      minCapacity: 20
      maxCapacity: 200
```

In `PlayerListener`, remove the `allocate()` / `ready()` branch and keep only `setCounterCount`.

#### Option B: Buffer autoscaler ("full" = Allocated)

When a server reaches the player limit it calls `allocate()`. The Fleet sees one less `Ready` server and starts a new one. When a player leaves, the server goes back to `Ready`.

```yaml
apiVersion: autoscaling.agones.dev/v1
kind: FleetAutoscaler
metadata:
  name: mc-autoscaler
spec:
  fleetName: mc-fleet
  policy:
    type: Buffer
    buffer:
      bufferSize: 2
      minReplicas: 2
      maxReplicas: 20
```

In `PlayerListener`, remove the `setCounterCount` call and the `counters:` block from the Fleet.

### 4. Deploy

```bash
kubectl apply -f fleet.yaml
kubectl apply -f autoscaler.yaml
kubectl get gameservers -w
```

A healthy server goes `Scheduled` -> `Ready`. Once players join (Option B and full) it shows `Allocated`.

## Configuration

| Setting | Where | Default |
|---|---|---|
| SDK port | env `AGONES_SDK_HTTP_PORT` | `9358` |
| Max players before "full" | `new PlayerListener(this, 20)` in `onEnable` | `20` |
| Counter capacity | `agones.setCounterCapacity("players", 20)` or the Fleet spec | `20` |
| Health interval | `runTaskTimerAsynchronously(..., 40L, 40L)` | 2 s |

Keep the plugin's max players, the Counter capacity and `max-players` in `server.properties` consistent.

## Project layout

```
de.reptudn.agonessync
├── AgonesSync.java        # plugin entry point: health ping, Ready, Shutdown
├── PlayerListener.java    # join/quit -> counter / allocate / ready
└── agones
    └── Agones.java        # REST client for the SDK sidecar + ServerState enum
```

## Agones states

| State | Set by |
|---|---|
| `PortAllocation`, `Creating`, `Starting`, `Scheduled`, `RequestReady` | Controller |
| `Ready` | SDK (`/ready`) |
| `Reserved` | SDK (`/reserve`) |
| `Allocated` | SDK (`/allocate`) or a `GameServerAllocation` |
| `Shutdown` | SDK (`/shutdown`) |
| `Unhealthy`, `Error` | Controller |

The plugin can only trigger `Ready`, `Reserved`, `Allocated` and `Shutdown`. Use `agones.fetchState()` to read the real state, since an external `GameServerAllocation` can change it without the plugin knowing.

## Troubleshooting

| Problem | Likely cause |
|---|---|
| Server turns `Unhealthy` right after start | `initialDelaySeconds` shorter than the boot time, or health pings not running |
| Stays in `Scheduled` | `/ready` never succeeded; check the plugin log and that the sidecar port is correct |
| Counter calls fail with 404 | `CountsAndLists` not enabled, counter not declared in the spec, or the endpoint path differs in your Agones version |
| Autoscaler doesn't react | Both strategies active, or the Counter key doesn't match (`players`) |
| `No matching variant of xyz.jpenilla:run-task` | Gradle wrapper older than 9.7.0 |

## License

Add your license here.
