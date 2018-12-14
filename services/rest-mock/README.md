A minimal mock server 
=========================

Allows you to test your services with a lightweight mock server

Usage
=========================

```scala


  implicit val system = ActorSystem("server")
  implicit val materializer = ActorMaterializer()
  implicit val myActor = system.actorOf(Props[RequestHandler], name = "handler")

  private val mockserver = Mockserver()
  
  // starts a server on a local port - here 8080
  private val stop = mockserver.startOnPort(8080)
  when get("/test") thenRespond(200,"""{"message":"ok"}""")
  when get("/test1") withHeaders ("header1"->"test","header2"->"test2") thenRespond(200,"""{"message":"ok1"}""")
  
  // reset all expectations
  mockserver.reset
  // start again 
  when get("/test1") thenRespond(200,"""{"message":"ok2"}""")
  // stop the server
  stop()

```

TODO
=========================
- Multiple expectations on the same route needs a fix