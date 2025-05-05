# RoyalMate - VAVA 2024/25

## Úvod

RoyalMate 👑 je simulátor internetovej herne poskytujúci hráčom široké spektrum zábavných kasíno hier (ruleta, sloty, coinflip,...). Program simuluje online kasíno a hráčov angažuje prostredníctvom hier aj komunitných nástrojov (rebríčky, chat).

Celé zadanie projektu a požiadavky na projekt sú dostupné tu: [Zadanie projektu a požiadavky na projekt](https://github.com/miroslav-reiter/VAVA_JAVA)

## Manažment projektu

K riešeniu prístupu riešiteľov k projektu bola využitá platforma GitHub, konkrétne zdieľaný verejný repozitár [RoyalMate GitHub Repository](https://github.com/xgabora/RoyalMate). Delegovanie a manažovanie aktuálnych otvorených bodov prebehlo v softvéri Jira. Riešenie celého projektu bolo rozdelené na 8 šprintov a spolu 46 taskov, pričom sme konzistenciu s GitHub commitmi udržiavali prostredníctvom Jira komentárov.

## Lokálne spustenie aplikácie

Existujú dva hlavné spôsoby, ako spustiť aplikáciu lokálne na vašom počítači: priamo zo zdrojového kódu pomocou vývojového prostredia (IDE) alebo spustením skompilovaného JAR súboru. Oba spôsoby vyžadujú, aby ste mali nainštalovanú kompatibilnú verziu Java Development Kit (JDK), konkrétne JDK 17 alebo novšiu, a aby bola správne nakonfigurovaná systémová premenná JAVA_HOME alebo aby bola Java dostupná v systémovej ceste (PATH).

Adresár so zdrojovým kódom k projektu a takisto .jar spustiteľným súborom nájdete tu: [RoyalMate Source Code and JAR](https://github.com/xgabora/RoyalMate/tree/main)

### 2.1 Zdrojový kód

Pre spustenie aplikácie v IDE je nevyhnutné mať prístup k súboru `Config.java` s údajmi pre prístup k databáze. Tento súbor je možné poskytnúť na vyžiadanie kontaktovaním xgabora@stuba.sk. Alternatívou je vytvorenie lokálnej databázy podľa diagramu tried v kapitole 6.1 a napojenie tejto databázy prostredníctvom vlastného `Config.java` súboru v balíku `util/`.

Návod na spustenie projektu:

1.  Otvorte projekt v IDE.
2.  Nechajte IDE stiahnuť všetky závislosti definované v `pom.xml` (Maven dependencies a v `module-info.java`).
3.  Nájdite hlavnú spúšťaciu triedu `sk.vava.royalmate.app.Main` alebo priamo `sk.vava.royalmate.app.RoyalMate`.
4.  Kliknite pravým tlačidlom myši na túto triedu a zvoľte možnosť "Run" alebo "Debug". IDE sa postará o kompiláciu kódu a spustenie aplikácie s potrebnými JavaFX modulmi. Uistite sa, že konfigurácia spustenia v IDE používa JDK 17+.

### 2.2 Spustenie JAR súboru

Toto je štandardný spôsob distribúcie a spúšťania finálnej aplikácie pre koncových používateľov. JAR súbor nájdete dostupný v GitHub release: [RoyalMate V1 Release](https://github.com/xgabora/RoyalMate/releases/tag/V1)

V prípade, že chcete JAR súbor skompilovať z fungujúceho kódu, využite Maven príkazy v terminále “mvn clean” na vyčistenie cieľového priečenka `target/` a potom “mvn install” na inštaláciu priečinka `target`. V rámci programu sa vďaka implementovaniu pluginu `maven-shade-plugin` vytvorí aj tzv. “fat jar”, teda .jar súbor so všetkými dôležitými a potrebnými knižnicami a súbormi.

Po úspešnom builde nájdete vygenerovaný JAR súbor v adresári `target/`.

Otvorte terminál alebo príkazový riadok, prejdite do adresára `target` a spustite JAR súbor pomocou príkazu:
`java -jar nazov-vasheho-jar-suboru.jar` (nahraďte `nazov-vasheho-jar-suboru.jar` skutočným názvom súboru, napr. `royalmate-1.0-SNAPSHOT-uber.jar`). Aplikácia by sa mala spustiť v novom okne. Prípadne môžete vo svojom prieskumníku súborov navigovať do priečinka `target/` a spustiť fat jar súbor dvojitým kliknutím.

Aplikácia RoyalMate vyžaduje pre správny beh stabilné pripojenie na internet. V prípade problémov so spustením .jar súboru odporúčame postupovať podľa nasledujúceho videa, ktoré poskytuje riešenia pre situácie, keď počítač nedokáže nájsť alebo načítať Java Runtime Environment. [Riešenie problémov so spustením Java aplikácií](https://www.youtube.com/watch?v=Uaml9ouqKNk) - Video od Guiding Tech o riešení problémov s Java Runtime Environment.
