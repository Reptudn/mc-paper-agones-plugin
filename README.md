# AgonesSync

The AgonesSync plugins exists to tell Agones wether the server is ready or not and if players are connected it tells
Agones that the server is allocated. It sends a shutdown signal if the server is shutting down. It also sends
periodic health checks to Agones to let it know the server is still alive.