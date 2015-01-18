RN-Beleg
========

*Vorgelegt von Leonard Hecker (s71628).*

Das Projekt kann wie vorgegeben Kompiliert und ausgeführt werden:
```sh
./make.sh
```

Danach stehen die vorgegebenen Befehle zur Verfügung
```sh
./server-udp <port> [<loss> <delay> [<veriation>]]
./client-udp <host> <port> <filepath>
```

Wie man bereits sehen kann, gibt es einen zusätzlichen Parameter `variation` für den Server, mit dem die relative oder absolute Variation des Delays gesteuert werden kann, um realere Tests zu ermöglichen.
Weitere Details zu den Parametern des Servers erhält man, wenn das Programm ohne Parameter ausgeführt wird.
