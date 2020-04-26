# Logger v1.0
---
Egyszerű program több log fájl olvasására.
### Funkciók
* Keresési érték megadása
* Időintervallum megadása 30 napig kiterjedőleg
* Típus kiválasztása (nincs korlátozva a típusok mennyisége)
### Használat
1. FTP szerver konfigurálása  
    * Felhasználónak a kezdő mappája annak a mappának kell lennie ahol a típusok mappái vannak
2. Fájl struktúrának a következőnek kell lennie:
    * típus _(pl: `debug`)_
      * típus_dátum.log _(pl: `debug_2020-04-26.log`)_
        * Fájl tartalma: `[HH:mm:ss.SSS] Szöveg`
3. Konfigurációs fájl szerkesztése  
    * A fájl neve: `config.xml` legyen, illetve a programmal egy mappában legyen
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <ftp>  
      <user>felhasználó</user>  
      <password>jelszó</password>  
      <host>host</host>  
      <port>port</port>  
    </ftp>
    ```
4. Program használata
    * Megnyitás után a következő képernyő vár minket.
      * Keresési feltételek
        * Érték: _Keresendő szöveget, üresen is hagyjatjuk_
        * Időintervallum: _Intervallum amiben keresni szeretnénk (`yyyy-MM-dd HH:mm:ss`)_
        * Típus: _Amiben keresünk, mindenképp ki kell választanunk_
      * Gombok
        * Keresés: _Megadott feltételekkel elindíthatjuk a lekérdezést_
        * Export: _Exportálhatjuk a keresési eredményt vele, célszerű HTML kiterjesztésbe menteni_
      * Keresési eredmény
        * Ha adtunk meg keresendő szöveget akkor csak azokat a sorokat fogja beolvasni a fájlokból amik tartalmazzák az adott szöveget, illetve pirossal ki lesznek jelölve a felületen.
        ![](https://i.imgur.com/Ow7JhVv.png)
