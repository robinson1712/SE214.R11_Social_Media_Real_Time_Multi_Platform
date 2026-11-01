import type { Person, Category, Topic, Conversation } from "./types";
export const initialPeople: Person[] = [
  [
    "Bogdan Nikitin",
    "bogdan_photo.png",
    "Designing things, collecting moments.",
    "Product designer · Kyiv",
  ],
  [
    "George Lobko",
    "george_avatar_circle.png",
    "Taking the long way home. Mountains, photographs and good company.",
    "Photographer · Lviv",
  ],
  [
    "Vitaliy Boyko",
    "vitaliy_avatar_circle.png",
    "Always looking for the next great cup of coffee.",
    "Coffee enthusiast · Kyiv",
  ],
  [
    "Nick Shelburne",
    "nick_avatar_circle.png",
    "Interfaces, small details and weekend adventures.",
    "Designer · London",
  ],
  [
    "Brittni Lando",
    "brittni_avatar_circle.png",
    "Cooking for friends and sharing what I learn.",
    "Food creator · Lisbon",
  ],
  [
    "Ivan Shevchenko",
    "ivan_avatar_circle.png",
    "Making music and meeting interesting people.",
    "Musician · Warsaw",
  ],
  [
    "Silena",
    "silena_avatar_circle.png",
    "Fresh air and new trails.",
    "Hiker · Zürich",
  ],
  [
    "Olya",
    "olya_avatar_circle.png",
    "Photos from everyday life.",
    "Photographer · Prague",
  ],
  [
    "Davis",
    "davis_avatar_circle.png",
    "Good music, better company.",
    "Creator · Berlin",
  ],
  ["Anatoly", "story1_avatar.png", "Out on the road.", "Traveler · Tbilisi"],
  [
    "Lolita Earns",
    "story2_avatar.png",
    "Chasing the evening light.",
    "Photographer · Porto",
  ],
].map(([name, avatar, bio, location], index) => ({ id: `artwork-${index}`, name, avatar, bio, location }));
export const categories: Category[] = [
  [
    "UI/UX",
    "rec_uiux.png",
    "Small details. Better interfaces.",
    "A place to share work, ask for feedback and talk about design.",
  ],
  [
    "Music",
    "rec_music.png",
    "Find your next favorite sound.",
    "Playlists, new discoveries and conversations with people who make music.",
  ],
  [
    "Cooking",
    "rec_cooking.png",
    "Good food brings us together.",
    "Home recipes, favorite cafés and things worth making this weekend.",
  ],
  [
    "Hiking",
    "rec_hiking.png",
    "There is more outside.",
    "Trail notes, mountain photographs and plans for the next adventure.",
  ],
].map(([name, image, title, description]) => ({
  name,
  image,
  title,
  description,
}));
export const initialTopics: Topic[] = [
  {
    id: "trail",
    category: "Hiking",
    title: "Your favorite trail for a quiet weekend?",
    author: "George Lobko",
    body: "I spent the weekend in the Alps. The early start was worth it. What trail would you go back to, and what should a first-time visitor know?",
    replies: [
      "Silena: Start early and bring an extra layer. The weather changes quickly.",
    ],
  },
  {
    id: "details",
    category: "UI/UX",
    title: "Show us a small design detail you love",
    author: "Nick Shelburne",
    body: "I have been collecting little interactions that make an interface feel thoughtful. Share a detail from your latest project or something you spotted this week.",
    replies: [],
  },
  {
    id: "coffee",
    category: "Cooking",
    title: "Coconut milk latte: what is your recipe?",
    author: "Vitaliy Boyko",
    body: "Tried a coconut milk latte today and now I want to make it at home. Do you steam the milk first or add it cold?",
    replies: [
      "Brittni Lando: Warm it gently first. A little cinnamon is lovely too.",
    ],
  },
  {
    id: "playlist",
    category: "Music",
    title: "The playlist that goes everywhere with you",
    author: "Ivan Shevchenko",
    body: "Putting together a soundtrack for the train ride this weekend. What is the one song you always come back to?",
    replies: [],
  },
];
export const emojiGroups = [
  [
    "faces",
    "😀",
    "Smileys",
    "😀 grinning face|😃 happy smile|😄 laughing smile|😁 beaming smile|😆 laughing|😅 nervous smile sweat|😂 tears joy haha|🤣 rolling laughing haha|😊 happy blush smile|😇 angel|🙂 smile|🙃 upside down|😉 wink|😌 relieved|😍 heart eyes love|🥰 smiling hearts love|😘 kiss love|😗 kiss|😙 kiss smile|😚 kiss blush|😋 delicious yummy|😛 tongue|😝 silly tongue|😜 wink tongue|🤪 crazy zany|🤨 raised eyebrow|🧐 monocle|🤓 nerd|😎 sunglasses cool|🥸 disguise|🤩 star eyes|🥳 party celebration|😏 smirk|😒 unamused|😞 disappointed|😔 sad pensive|😟 worried|😕 confused|🙁 sad frown|☹️ frown|😣 persevering|😖 confounded|😫 tired|😩 weary|🥺 pleading|😢 sad cry|😭 sobbing cry|😤 triumph steam|😠 angry|😡 angry rage|🤬 swearing|🤯 exploding mind wow|😳 flushed surprised|🥵 hot|🥶 cold|😱 scream scared|😨 fearful|😰 anxious sweat|😥 sad relieved|😓 sweat|🤗 hug|🤔 thinking|🫣 peeking|🤭 giggling hand mouth|🤫 quiet shush|🤥 lying|😶 speechless|😐 neutral|😑 expressionless|😬 grimace|🙄 eye roll|😯 surprised wow|😮 wow open mouth|😲 astonished wow|🥱 yawn sleepy|😴 sleeping|🤤 drool|😪 sleepy|😮‍💨 sigh exhale|😵 dizzy|🤐 zipper mouth|🥴 woozy|🤢 sick nauseous|🤮 vomiting|🤧 sneeze|😷 mask|🤒 fever|🤕 bandage|🤑 money|🤠 cowboy|😈 devil|👿 angry devil|👻 ghost|💀 skull|☠️ skull bones|👽 alien|🤖 robot|🎃 pumpkin|😺 cat smile|😸 cat grin|😹 cat joy|😻 cat love|😼 cat smirk|😽 cat kiss|🙀 cat scared|😿 cat cry|😾 cat angry",
  ],
  [
    "people",
    "👋",
    "People & gestures",
    "👋 wave hello|🤚 raised hand|🖐️ hand fingers|✋ stop hand|🖖 vulcan|👌 okay|🤌 pinched fingers|🤏 pinch|✌️ peace victory|🤞 crossed fingers luck|🤟 love you|🤘 rock horns|🤙 call me|👈 point left|👉 point right|👆 point up|👇 point down|☝️ index up|👍 thumbs up like|👎 thumbs down|✊ fist|👊 fist bump|🤛 left fist|🤜 right fist|👏 applause clap|🙌 celebration hands|👐 open hands|🤲 palms up|🤝 handshake|🙏 thank you pray|✍️ writing|💅 nail polish|🤳 selfie|💪 strong muscle|🦾 mechanical arm|🦵 leg|🦶 foot|👂 ear|👃 nose|🧠 brain|🫀 heart organ|🫁 lungs|🦷 tooth|🦴 bone|👀 eyes|👁️ eye|👅 tongue|👄 lips|👶 baby|🧒 child|👦 boy|👧 girl|🧑 person|👨 man|👩 woman|👴 grandfather|👵 grandmother|🙋 raising hand|🙆 okay person|🙅 no person|🤷 shrug|🤦 facepalm|💁 information|🙇 bow|🧘 meditation|🏃 running|🚶 walking|💃 dancing|🕺 dancing man|👯 dancers|🧑‍💻 developer|🧑‍🎨 artist|🧑‍🍳 chef|🧑‍🚀 astronaut|👑 crown",
  ],
  [
    "nature",
    "🌿",
    "Animals & nature",
    "🐶 dog|🐱 cat|🐭 mouse|🐹 hamster|🐰 rabbit|🦊 fox|🐻 bear|🐼 panda|🐨 koala|🐯 tiger|🦁 lion|🐮 cow|🐷 pig|🐸 frog|🐵 monkey|🙈 see no evil|🙉 hear no evil|🙊 speak no evil|🐔 chicken|🐧 penguin|🐦 bird|🐤 chick|🦆 duck|🦅 eagle|🦉 owl|🦇 bat|🐺 wolf|🐗 boar|🐴 horse|🦄 unicorn|🐝 bee|🪲 beetle|🐛 caterpillar|🦋 butterfly|🐌 snail|🐞 ladybug|🐜 ant|🕷️ spider|🦂 scorpion|🐢 turtle|🐍 snake|🦎 lizard|🦖 dinosaur|🐙 octopus|🦑 squid|🦐 shrimp|🦀 crab|🐠 fish|🐬 dolphin|🐳 whale|🦈 shark|🐘 elephant|🦒 giraffe|🦓 zebra|🦍 gorilla|🦧 orangutan|🦘 kangaroo|🦥 sloth|🦦 otter|🦔 hedgehog|🐾 paw prints|🌵 cactus|🎄 christmas tree|🌲 evergreen|🌳 tree|🌴 palm|🌱 seedling|🌿 herb leaf|☘️ shamrock|🍀 lucky clover|🍃 leaves wind|🍂 fallen leaf|🍁 maple|🌷 tulip|🌹 rose|🌺 hibiscus|🌸 blossom|🌼 flower|🌻 sunflower|🌞 sun face|🌝 full moon|🌙 crescent moon|⭐ star|🌟 glowing star|✨ sparkles|⚡ lightning|🔥 fire|🌈 rainbow|☀️ sun|🌤️ cloudy sun|☁️ cloud|🌧️ rain|⛈️ storm|❄️ snow|☃️ snowman|💨 wind|💧 droplet|🌊 ocean wave",
  ],
  [
    "food",
    "🍓",
    "Food & drinks",
    "🍏 green apple|🍎 red apple|🍐 pear|🍊 orange|🍋 lemon|🍌 banana|🍉 watermelon|🍇 grapes|🍓 strawberry|🫐 blueberry|🍈 melon|🍒 cherry|🍑 peach|🥭 mango|🍍 pineapple|🥥 coconut|🥝 kiwi|🍅 tomato|🍆 eggplant|🥑 avocado|🥦 broccoli|🥬 greens|🥒 cucumber|🌶️ chili|🌽 corn|🥕 carrot|🧄 garlic|🧅 onion|🥔 potato|🥐 croissant|🥖 baguette|🍞 bread|🥨 pretzel|🥯 bagel|🥞 pancake|🧇 waffle|🧀 cheese|🥚 egg|🍳 cooking egg|🥓 bacon|🥩 steak|🍗 chicken|🍖 meat|🌭 hot dog|🍔 burger|🍟 fries|🍕 pizza|🥪 sandwich|🌮 taco|🌯 burrito|🥙 kebab|🧆 falafel|🍜 noodles pho|🍲 soup|🍛 curry|🍣 sushi|🍱 bento|🥟 dumpling|🍤 fried shrimp|🍙 rice ball|🍚 rice|🍘 rice cracker|🍥 fish cake|🥠 fortune cookie|🥮 moon cake|🍢 skewer|🍡 dango|🍧 shaved ice|🍨 ice cream|🍦 soft serve|🥧 pie|🧁 cupcake|🍰 cake|🎂 birthday cake|🍮 pudding|🍭 lollipop|🍬 candy|🍫 chocolate|🍿 popcorn|🍩 donut|🍪 cookie|🌰 chestnut|🥜 peanut|🍯 honey|🥛 milk|🍼 baby bottle|☕ coffee tea|🫖 teapot|🍵 green tea|🧃 juice|🥤 soda|🧋 bubble tea|🍶 sake|🍺 beer|🍻 cheers beer|🥂 champagne toast|🍷 wine|🍸 cocktail|🍹 tropical drink|🧉 mate|🧊 ice",
  ],
  [
    "travel",
    "✈️",
    "Travel & places",
    "🚗 car|🚕 taxi|🚙 suv|🚌 bus|🚎 trolley|🏎️ racing car|🚓 police car|🚑 ambulance|🚒 fire truck|🚐 van|🚚 truck|🚲 bicycle|🛴 scooter|🏍️ motorcycle|🛵 motor scooter|🚂 train|🚆 train|🚇 metro|🚊 tram|✈️ airplane|🛫 departure|🛬 arrival|🚀 rocket|🛸 flying saucer|🚁 helicopter|⛵ sailboat|🛶 canoe|🚤 speedboat|🛳️ ship|⛴️ ferry|⚓ anchor|🗺️ map|🧭 compass|🏔️ snowy mountain|⛰️ mountain hiking|🌋 volcano|🗻 mount fuji|🏕️ camping|🏖️ beach|🏜️ desert|🏝️ island|🏞️ national park|🏟️ stadium|🏛️ building|🏗️ construction|🏠 home|🏡 garden house|🏢 office|🏥 hospital|🏦 bank|🏨 hotel|🏫 school|🏰 castle|🗼 tower|🗽 liberty|⛩️ shrine|🕌 mosque|⛪ church|🌅 sunrise|🌄 mountain sunrise|🌇 sunset|🌆 city dusk|🌃 city night|🌉 night bridge|🌌 galaxy|🎠 carousel|🎡 ferris wheel|🎢 roller coaster|⛲ fountain|⛱️ umbrella|🚦 traffic light|⛽ fuel|🛤️ railway|🛣️ road",
  ],
  [
    "activity",
    "⚽",
    "Activities",
    "⚽ soccer football|🏀 basketball|🏈 football|⚾ baseball|🥎 softball|🎾 tennis|🏐 volleyball|🏉 rugby|🎱 billiard|🏓 ping pong|🏸 badminton|🏒 hockey|🏑 field hockey|🥍 lacrosse|🏏 cricket|🥅 goal|⛳ golf|🏹 archery|🎣 fishing|🥊 boxing|🥋 martial arts|🎽 running|🛹 skateboard|🛼 roller skate|🛷 sled|⛸️ ice skate|🥌 curling|🎿 ski|⛷️ skiing|🏂 snowboard|🪂 parachute|🏋️ weightlifting|🤸 gymnastics|🤼 wrestling|🤽 water polo|🤾 handball|🏊 swimming|🚴 cycling|🧗 climbing|🏄 surfing|🚣 rowing|🏆 trophy|🥇 gold medal|🥈 silver medal|🥉 bronze medal|🏅 medal|🎖️ medal award|🎗️ ribbon|🎫 ticket|🎟️ ticket|🎭 theater|🎨 art palette|🎬 movie|🎤 microphone|🎧 headphones|🎼 music score|🎹 piano|🥁 drum|🪘 drum|🎷 saxophone|🎺 trumpet|🪗 accordion|🎸 guitar|🎻 violin|🪕 banjo|🎲 dice|♟️ chess|🎯 target|🎳 bowling|🎮 game controller|🕹️ joystick|🧩 puzzle|🎰 slot",
  ],
  [
    "objects",
    "💡",
    "Objects",
    "⌚ watch|📱 mobile phone|💻 laptop|⌨️ keyboard|🖥️ computer|🖨️ printer|🖱️ mouse|💽 disk|💾 floppy save|💿 disc|📷 camera|📸 camera flash|📹 video camera|🎥 film camera|📽️ projector|📺 television|📻 radio|🎙️ studio microphone|⏰ alarm clock|⏳ hourglass|🔋 battery|🔌 plug|💡 light idea|🔦 flashlight|🕯️ candle|🪔 lamp|🧯 extinguisher|💸 money|💵 dollar|💶 euro|💷 pound|💰 money bag|💳 credit card|💎 diamond|⚖️ scales|🧰 toolbox|🔧 wrench|🔨 hammer|🛠️ tools|⚙️ gear|🔩 nut bolt|🧱 brick|🧲 magnet|🔬 microscope|🔭 telescope|🧪 test tube|💊 pill|🩹 bandage|🩺 stethoscope|🚪 door|🪞 mirror|🪟 window|🛏️ bed|🛋️ sofa|🪑 chair|🚿 shower|🛁 bath|🪴 plant|🧹 broom|🧺 basket|🧻 paper|🧼 soap|🪥 toothbrush|🎁 gift|🎈 balloon|🎉 party popper|🎊 confetti|🎀 bow|🪄 magic wand|📩 envelope|💌 love letter|📦 package|📚 books|📖 open book|📝 note|✏️ pencil|🖊️ pen|🖌️ paintbrush|📌 pin|📍 location pin|📎 paperclip|📏 ruler|✂️ scissors|🔒 lock|🔓 unlock|🔑 key|🗝️ key|🔔 bell",
  ],
  [
    "symbols",
    "❤️",
    "Hearts & symbols",
    "❤️ red heart love|🧡 orange heart|💛 yellow heart|💚 green heart|💙 blue heart|💜 purple heart|🖤 black heart|🤍 white heart|🤎 brown heart|💔 broken heart|❤️‍🔥 heart fire|❤️‍🩹 healing heart|❣️ heart exclamation|💕 two hearts|💞 revolving hearts|💓 beating heart|💗 growing heart|💖 sparkling heart|💘 cupid heart|💝 gift heart|💟 heart decoration|💯 hundred perfect|💢 anger|💥 collision boom|💫 dizzy star|💦 sweat drops|💤 sleep zzz|💬 speech bubble|🗯️ anger bubble|💭 thought|✅ check|☑️ checkbox|✔️ check mark|❌ cross|❎ cross button|❗ exclamation|❓ question|‼️ double exclamation|⁉️ exclamation question|⚠️ warning|🚫 prohibited|⛔ no entry|♻️ recycle|🔰 beginner|🔱 trident|⚜️ fleur de lis|🔴 red circle|🟠 orange circle|🟡 yellow circle|🟢 green circle|🔵 blue circle|🟣 purple circle|⚫ black circle|⚪ white circle|🟥 red square|🟧 orange square|🟨 yellow square|🟩 green square|🟦 blue square|🟪 purple square|⬛ black square|⬜ white square|🔶 orange diamond|🔷 blue diamond|🔺 red triangle|🔻 down triangle|▶️ play|⏸️ pause|⏹️ stop|⏭️ next|⏮️ previous|🔀 shuffle|🔁 repeat|🔂 repeat one|🔈 speaker|🔉 speaker|🔊 volume|🔇 mute|🎵 music note|🎶 music notes|➕ plus|➖ minus|➗ divide|✖️ multiply|♾️ infinity|©️ copyright|®️ registered|™️ trademark",
  ],
  [
    "flags",
    "🏳️",
    "Flags",
    "🏳️ white flag|🏴 black flag|🏁 checkered flag|🚩 red flag|🏳️‍🌈 rainbow pride flag|🏳️‍⚧️ transgender flag|🏴‍☠️ pirate flag|🇻🇳 Vietnam flag|🇯🇵 Japan flag|🇰🇷 South Korea flag|🇨🇳 China flag|🇹🇭 Thailand flag|🇸🇬 Singapore flag|🇮🇩 Indonesia flag|🇲🇾 Malaysia flag|🇵🇭 Philippines flag|🇮🇳 India flag|🇦🇺 Australia flag|🇳🇿 New Zealand flag|🇺🇸 United States flag|🇨🇦 Canada flag|🇲🇽 Mexico flag|🇧🇷 Brazil flag|🇦🇷 Argentina flag|🇬🇧 United Kingdom flag|🇫🇷 France flag|🇩🇪 Germany flag|🇮🇹 Italy flag|🇪🇸 Spain flag|🇵🇹 Portugal flag|🇳🇱 Netherlands flag|🇨🇭 Switzerland flag|🇸🇪 Sweden flag|🇳🇴 Norway flag|🇩🇰 Denmark flag|🇫🇮 Finland flag|🇺🇦 Ukraine flag|🇵🇱 Poland flag|🇹🇷 Turkey flag|🇿🇦 South Africa flag|🇪🇬 Egypt flag",
  ],
];
export const locations = [
  ["Ho Chi Minh City", "Vietnam · City", "Saigon Sài Gòn"],
  ["Hanoi", "Vietnam · City", "Hà Nội"],
  ["Da Nang", "Vietnam · Coastal city", "Đà Nẵng"],
  ["Da Lat", "Vietnam · Highlands", "Đà Lạt Dalat"],
  ["Hoi An", "Vietnam · Old town", "Hội An"],
  ["Phu Quoc", "Vietnam · Island", "Phú Quốc"],
  ["Alps, Switzerland", "Switzerland · Mountain range", "Swiss Alps"],
  ["Kyoto", "Japan · City", "京都"],
  ["Tokyo", "Japan · City", "東京"],
  ["Seoul", "South Korea · City", "서울"],
  ["Singapore", "Singapore · City", ""],
  ["Bangkok", "Thailand · City", ""],
  ["Paris", "France · City", ""],
  ["London", "United Kingdom · City", ""],
  ["Lisbon", "Portugal · City", "Lisboa"],
  ["New York", "United States · City", "NYC"],
];
export const initialConversations: Record<string, Conversation> =
  Object.fromEntries([
    [
      "George Lobko",
      {
        unread: 2,
        messages: [
          {
            text: "That mountain view was incredible. We should go back in the autumn.",
            own: false,
          },
          {
            text: "Count me in. Send me the trail when you get a chance.",
            own: true,
          },
          {
            text: "Absolutely! I will share the route this evening.",
            own: false,
          },
        ],
      },
    ],
    [
      "Olya",
      {
        unread: 1,
        messages: [
          { text: "Are you coming to the photo walk on Saturday?", own: false },
        ],
      },
    ],
    [
      "Nick Shelburne",
      {
        unread: 3,
        messages: [
          {
            text: "Thanks for the feedback on my latest design. The new version is ready to share.",
            own: false,
          },
        ],
      },
    ],
  ]);
export const normalize = (text: string) =>
  text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replaceAll("đ", "d");
