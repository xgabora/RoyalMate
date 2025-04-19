# RoyalMate

**RoyalMate 👑** je simulátor internetovej herne poskytujúci hráčom široké spektrum zábavných kasíno hier (ruleta, sloty, coinflip). Program simuluje online kasíno a hráčov angažuje prostredníctvom hier aj komunitných nástrojov (rebríčky, chat).

Projekt RoyalMate je semestrálnou prácou v rámci predmetu **VAVA_B** na FIIT STU.

# AKTUÁLNE TASKY

**ADAM:** 

 - vytvoriť DB model a ostatné EA súbory
 - vytvoriť Figma UI návrh
 - dokončiť grafické návrhy textúr
 - vytvoriť databázu a prepojiť ju s  projektom

**VŠETCI ČLENOVIA TÍMU:** 

- pridať sa do Discord kanála ([https://discord.gg/K4yTXsME](https://discord.gg/K4yTXsME))
 - zapísať sa do RACI matice (https://docs.google.com/spreadsheets/d/1JBKc4BeDQtOA-quhslUQo3Hlfsjiwf6uL1vK1YDgjmE/edit?usp=sharing)
 - vyskúšať si pull-núť tento repozitár, spustiť vzorovú prázdnu aplikáciu (JDK 17, IntelliJ ideálne)
 - oboznámiť sa s JIRA Software (pozvánku máte v mejle)
 - rozmýšľať nad svojou funkcionalitou - čo sa dá spraviť, ako
 - pre ideálny štart s JAVAFX obetovať jedno poobedie / večer času: https://www.youtube.com/watch?v=9XJicRt_FaI&ab_channel=BroCode

## 5W
**WHO - KTO?** 
RoyalMate primárne cieli na B2C segment, konkrétne na mladých dospelých vo veku 21-40 rokov, ktorí majú záujem o online hazardné hry v bezpečnom a regulovanom prostredí. Naša primárna demografická skupina sú digitálne zdatnejší jednotlivci, ktorí oceňujú kombináciu zábavy, sociálnej interakcie a možnosti výhry. Vzhľadom na vzdelávací účel simulátora tiež sekundárne a okrajovo cielime na segment študentov informatiky a herného dizajnu, ktorí môžu študovať mechanizmy hazardných hier bez finančných rizík.

**WHY - PREČO?** 
RoyalMate je komplexná simulačná platforma online kasína, ktorá spája adrenalín z hazardných hier s bezpečným prostredím bez rizika skutočných finančných strát. Aplikácia vytvára realistický zážitok z online herní vrátane populárnych hier ako ruleta, sloty či koleso šťastia, pričom dopĺňa herný zážitok o komunitné prvky ako chat a rebríčky. Fundamentálnym účelom RoyalMate je poskytnúť zábavu, súťaživé prostredie a sociálnu interakciu, zatiaľ čo zároveň zvyšuje povedomie o mechanizmoch a pravdepodobnostiach v hazardných hrách.

Biznis model RoyalMate je postavený na free princípe, pričom sú všetky funkcionality aplikácie použávateľovi dostupné zadarmo. Aplikácia vytvára hodnotu tým, že poskytuje používateľom bezpečnú alternatívu k skutočnému hazardu, ponúka edukatívnu vrstvu o pravdepodobnosti a rizikách, a buduje lojálnu komunitu hráčov. V dlhodobom horizonte RoyalMate môže slúžiť ako platforma pre partnerskú spoluprácu s legálnymi poskytovateľmi hazardných hier, organizovanie turnajov alebo ako vzdelávací nástroj pre pochopenie dynamiky či marketingu hazardných hier.


**WHEN - KEDY?** 
Používatelia pristupujú k RoyalMate v rôznych časových vzorcoch, najmä však počas večerných hodín (18:00 - 23:00) v pracovných dňoch a flexibilnejšie počas víkendov. Analýza používateľského správania očakáva, že priemerný používateľ spustí aplikáciu 3- až 4-krát týždenne, s priemernou dĺžkou relácie 30 - 45 minút. Špičky aktivity sa objavujú počas piatkových a sobotných večerov, kedy používatelia trávia v aplikácii dlhšie časové úseky a aktívnejšie sa zapájajú do komunitných funkcií ako chat či turnaje. RoyalMate je najčastejšie využívaný v situáciách, keď používatelia hľadajú krátku formu zábavy v čase oddychu - napríklad po práci, počas cestovnej dopravy, alebo ako sociálnu aktivitu s priateľmi cez víkend. Významné používateľské aktivácie sú evidované počas špeciálnych udalostí ako virtuálne turnaje alebo nepravidelné pridanie nových hier, ktoré je strategicky plánované na udržanie angažovanosti používateľov

**WHERE - KDE?** 
RoyalMate je desktopová aplikácia vyžadujúca počítač alebo laptop s operačným systémom Windows 10/11, macOS alebo Linux s minimálne 4 GB RAM, 2 GHz dvojjadrovým procesorom a 500 MB voľného diskového priestoru. Pre využívanie aplikácie je potrebné stabilné pripojenie na internet s rýchlosťou minimálne 10 Mbps. Aplikácia je optimalizovaná pre obrazovky s rozlíšením 1920x1080 a vyšším. Z technického hľadiska RoyalMate vyžaduje inštaláciu Java Runtime Environment (JRE) 11 alebo novšej verzie. Vzhľadom na sociálny aspekt aplikácie je dôležité zabezpečiť stabilnú a bezpečnú dátovú komunikáciu, preto aplikácia implementuje šifrovanú komunikáciu a zabezpečené pripojenie k centrálnemu serveru. V budúcich verziách plánujeme implementovať cloudové zálohovanie používateľských dát a profilov, čo umožní plynulý prechod medzi rôznymi zariadeniami a lokáciami bez straty herného progresu.

**HOW - AKO?** 
Obchodný proces RoyalMate začína získavaním používateľov cez digitálne marketingové kanály s dôrazom na sociálne médiá a platformy pre hráčov. Nový používateľ sa registruje pomocou e-mailu a získava uvítací bonus vo forme voľných točení. Následne prechádza onboardingom, ktorý ho na domovskej obrazovke zoznámi s hlavnými funkciami a hrami. Typ hazardnej hry si potom môže zvoliť podľa svojej používateľskej preferencie, a takisto je mu umožnené interagovať s ostatnými používateľmi prostredníctvom komunitných nástrojov. 

Špeciálnym typom používateľa je superuser “Administrátor”, ktorý dokáže manažovať príspevky v komunitnom čete, meniť vzhľad domovskej stránky aj pridávať nové hry do kasína a pripisovať používateľom (hráčom) na ich hráčske konto odmeny. 

Kľúčovým obchodným procesom je udržanie používateľskej angažovanosti prostredníctvom pravidelných udalostí a stimulov. Systém odmien motivuje k pravidelnému prihlasovaniu, turnaje podporujú súťaživosť a lojalitu. Sociálne funkcie ako chat či zdieľanie výsledkov budujú komunitu.

**HOW MUCH - KOĽKO?** 
Reálny vývoj aplikácie RoyalMate v rozsahu uvedených funkcionalít vyžaduje vývojový tím pozostávajúci z 6-10 členov pracujúcich po dobu 3-4 mesiacov. Fiktívny rozpočet na tento projekt sa odhaduje v rozmedzí 25 000 € - 40 000 €, pričom táto suma zahŕňa náklady na návrh, vývoj, testovanie a implementáciu základnej verzie aplikácie.

Najväčšiu časť rozpočtu tvorí práca vývojárov (približne 80 - 85 % celkovej sumy), kde rátame s hodinovou sadzbou 15 - 35 € závislosti od seniority a špecializácie. Práca UI/UX dizajnéra predstavuje približne 5 - 10 % rozpočtu a zvyšných 5 % tvoria náklady na projektový manažment.

Okrem priamych nákladov na vývoj je potrebné počítať s dodatočnými výdavkami v rozmedzí 5 000€ - 8 000 € na licencie pre vývojové nástroje a softvér (IDE, grafické programy, licencie pre knižnice), školenia vývojového tímu v oblasti JavaFX a náklady na základnú infraštruktúru vrátane testovacej databázy a vývojových serverov. Tieto náklady nerátajú s prevádzkovými výdavkami po nasadení aplikácie, ako sú hosting, údržba, aktualizácie a zákaznícka podpora.

V prípade rozšírenia projektu o mobilnú aplikáciu alebo pokročilejšie funkcie je potrebné počítať s dodatočnými nákladmi v rozmedzí 15 000 € - 30 000 € v závislosti od komplexnosti požadovaných rozšírení. Celkové náklady na reálne zhotovenie aplikácie RoyalMate v nami naplánovanom rozsahu sú teda v rozmedzí 30 000 až 50 000 eur s odchýlkou 10 % zo sumy v oboch smeroch.
