# Google Play store listing — Квизић (draft)

Paste into Play Console → Grow users → Store presence → Main store listing. Serbian is the default language
(`sr`); English (`en-US`) is a translation. Every claim here is something the app does today.

## Serbian (default)

**App name** (30 max)

```
Квизић: квиз са друштвом
```

**Short description** (80 max)

```
Иста питања, исти сат: ко од вас зна више и брже? Квиз уживо, до осам играча.
```

**Full description** (4000 max)

```
Квизић је квиз уживо за друштво. Направи собу, пошаљи кôд пријатељима и играјте заједно: сви добијају исто питање у исто време, а бодове носи ко зна, и ко зна брже.

• До осам играча у соби: у приватној, у коју се улази кôдом, или у јавној, у коју може свако.
• Брза игра те одмах смешта у прву слободну собу.
• Водитељ бира колико питања, колико времена за одговор, теме и тежину, и да ли погрешан одговор кошта бодова.
• Петнаест тема: географија, историја, спорт, музика, филм и серије, наука и технологија, језик и књижевност, храна и пиће, природа и животиње, уметност, митологија, тело и здравље, возила, игре, стрипови и цртани.
• Чим се одлучиш, видиш шта су изабрали други, а после сваког питања тачан одговор, често уз кратко објашњење, и табелу.
• Нема ћаскања: само реакције, од бравоа до аплауза.
• Можеш да играш и сам и да обараш свој рекорд.
• Ниво ти расте са сваком игром одиграном у соби.

Без реклама, без куповина и без регистрације: играш одмах, а налог можеш да повежеш са Google Play играма.
```

## English (en-US)

**App name**

```
Kvizić: trivia with friends
```

**Short description**

```
Same questions, same clock: who knows more, and faster? Live trivia for up to 8.
```

**Full description**

```
Kvizić is a live trivia game for friends. Make a room, send the code and play together: everyone gets the same question at the same time, and the points go to whoever knows, and knows it faster.

• Up to eight players in a room: a private one you join by code, or a public one anyone can join.
• Quick play puts you in the first open room at once.
• The host picks how many questions, how long to answer, the topics and the difficulty, and whether a wrong answer costs points.
• Fifteen topics, from geography, history and sport to music, film, science, food, nature, art and mythology.
• Once you lock in, you see what the others picked; after each question, the right answer, often with a short explanation, and the standings.
• No chat: only reactions, from bravo to applause.
• Play alone too, and beat your own best.
• Your level rises with every game you finish in a room.

No ads, no purchases, no sign-up: play at once, and link your account to Google Play Games if you like.

The questions are in Serbian.
```

## Graphics

- **App icon** 512×512 PNG: [store/icon-512.png](store/icon-512.png), the iOS 1024 tile scaled down (Play rounds
  the corners itself).
- **Feature graphic** 1024×500: [store/feature-graphic-1024x500.png](store/feature-graphic-1024x500.png), the
  sign on the stage as the app draws it; `KVIZIC_DESIGN_DIR=… ./gradlew :app:shared:jvmTest --tests
  '*StoreArtTest*'` draws it again.
- **Phone screenshots**, 2–8, 9:16, at least 1080×1920: real-device captures, in this order:
  Home · a question being answered with picks on the tiles · the reveal's board · the results' podium ·
  the lobby with seats filled · the room's settings.

## Categories and contact

- Category: **Game → Trivia**. Tags: Trivia, Multiplayer, Quiz.
- Email: application.eili@gmail.com · Website: https://kvizic.ntole.com · Privacy policy:
  https://kvizic.ntole.com/privacy.html
