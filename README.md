mtoxy
=====

**mtoxy** is a Java implementation of a Telegram MTProto proxy protocol. It has the following features:

* Fully asynchronous and non-blocking: 4 threads to handle a lot of connections;
* Supports `epoll` or `kqueue`-based transport on Linux/MacOS/BSD to further increase performance;
* Built-in support for HTTP passthrough: some public Wi-Fi networks block connections to a non-standard ports, so run your proxy on 80 port without having to sacrifice your web server;
* Proxy through proxy: this is a feature that is mainly useful at development, since I cannot reach Telegram servers directly in Russia;
* Collection of a traffic statistics.

Setting it up
=============

Download the latest release, create a config file `config.conf` like this and run JAR — that's all! Proxy will listen on 8443 port by default.

```
mtoxy {
  secret-passphrase = "correct-horse-battery-staple"
}
```

To run the JAR you should use `java -jar mtoxy-<...>.jar` command.

Configuration
=============

In addition to specifying secret key the configuration file has some advanced options:

```
mtoxy {
  # Secret key to use with proxy, specified as-is in HEX.
  secret = "00112233445566778899aabbccddeeff"
  
  # Secret key to use with proxy, specified as some passphrase that will be hashed
  secret-passphrase = "correct-horse-battery-staple"

  # Peers to connect. By default they are Telegram datacenters, but you can specify your own.
  # With "direct://..." URLs the connection will be made using Obufscated2 protocol without a secret key
  # With "proxy://.../<secret>" URLs the connection will be made using modified Obfuscated2 protocol with a secret.
  # So if you want to use another proxy as your upstream - specify it here as your only peer.
  peers = [
    "direct://149.154.175.50",
    "direct://149.154.167.51",
    "direct://149.154.175.100",
    "direct://149.154.167.91",
    "direct://149.154.171.5"
  ]

  # A list of address that the proxy will listen on. 
  # By default port 8443 is used, but you can add as many ports as you want.
  listen-addresses = [
    "0.0.0.0:8443"
  ]

  # An optional value that is needed when you want to use HTTP passthrough.
  # mtoxy will forward all HTTP requests to the specified address.
  # In case when this option is not specified all incoming HTTP requests will be dropped.
  http-backend = "127.0.0.1:81"
  
  # Number of threads to use on a "boss" server socket. 
  # Single thread is sufficient for almost all use cases.
  boss-threads = 1
  
  # Worker threads to use. All AES encryption and forwarding logic will be run on these threads.
  # Set it to the number of your CPU cores.
  worker-threads = 4
  
  # Network transport to use. Allowed values:
  # * 'nio': Java NIO transport, works on most operating systems
  # * 'epoll': epoll()-based transport, works only on Linux
  # * 'kqueue': kqueue()-based transport, works only on MacOS/BSD
  network-transport = nio
  
  # Interval between statistics reports to console. Set to 0 to disable.
  statistics-report-interval = 1m
}
```