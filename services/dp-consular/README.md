### Service registration and discovery

Data plane by design is a distributed application composed of multiple services
which run in separate processes and may even be running as multiple instances. 

Services talk to each other over an API gateway, and the registration and discovery
 mechanism is implemented in consul.
 
The mechanism is described as follows
 
 * Each service should use this client (unless they take care of registry themselves) and initialize the ApplicationRegistrar by passing in a 
 config similar to as shown below, this config can be simply placed into the application.conf of your service
 
 ```hocon
consul {
  #unique name
  serviceId = "clusters_01"
  #common name across instances
  serviceName = "clusters"
  service.tags = ["cluster-service"]
  service.port = 9009
  client.connect.failure.retry.secs = 5
  host = "localhost"
  port = 8500
}
```
* This configuration declares some properties about the client and the location of the consul agent
to register with
* The service Id must be unique, and multiple instances should share the same service name
* The registrar registers the service on boot, and also sets up a /health endpoint which must be 
implemented by the service
* The registrar communicates its state/status through the ```com.hortonworks.datapalane.consul.CosulHook```
and this must be passed in to exercise any control over the registration status.

#### Api gateway

The API gateway used is a Zuul edge proxy which uses registers itself as ```zuul``` in consul

* Your application may choose to be gateway aware, that means implementing the following config and 
instantiating the gateway
* When using the gateway there is no requirement for knowing the location of the system being called
* The dataplane gateway needs a one time config set up and restart  when adding a new service 

```hocon
gateway {
  ssl.enabled = false
  refresh.servers.secs = 60
}
```
* The gateway discovery process picks up a random zuul server available in consul
* The gateway process will find zuul and set up properties in System.properties, which can be used
to talk to other services over the gateway
* if the gateway finds that there are multiple instances of a service, it will balance it in a round robin
fashion




 

