# Modern Companions Changelog

## v1.1.2
* Can now set custom companion skins using /companionskin "NAME" URL | Example: `/companionskin "Daniel George" https://i.imgur.com/FWADR65.png`

## v1.1.3
* Attack/Cast/Eat/Drink Animations fixed

## v1.1.4
* Extended Companion is hungry messages
* Resurrection Scrolls now invulnerable - They resist gravity/fire/lava/explosions and will avoid the void.

## v1.2.0
* Realigned player inventory in Companion GUI
* Added Optional Curio support for Companions
    * Curio visiblity is toggleable
* Added Optional Sophisticated Backpack support/feature
    * If a Companion is wearing a Sophisticated Backpack, they will fill it before their inventory.
* Added better preferred weapon and fallback support.
    * Companions using a preferred weapon type will recieve a +2 bonus to their damage.
    * Companions will now use whatever weapon is available if they do not have their preferred weapon.
    * Hopefully increased Shield support for Vanguards - should equip modded shields now.
    * Fixed bug where Vanguard equipped 2 shields.
* Companion Traits, Backgrounds, Morale, Bonds and additional Stats, and more!
    * Companions now spawn with 1-2 random traits which will grant small bonuses.
    * Companions will now spawn with a small backstory.
    * Companions now have a 'Morale' level - taking care of them raises this while neglecting them will drain it.
    * The more you travel, heal, and revive you companion the stronger their Bond will grow. This will grant benefits to the Companion.
    * There is now an additional button in the Companion GUI that will lead you to a new Biography page.
    * Companions now have ages assigned to them, and will age 1 year every 3 in-game months.
        * Aging is only a visual string in the Bio page.
    * Existing Companions will have missing values (Backstory, Age, Traits) assigned to them.

# v1.3.0
* Introduced Jobs
    * Implemented Jobs
        * Fisher
            * Fisher will path to nearest water, and begin fishing until taken off patrol.
            * Fisher will produce random fishing loot, as if the player were fishing.
        * Miner
            * Miners will catalogue all ores in their work radius, path to them and retrieve them. (Modded ores included!)
            * Miners will break all stone-like blocks, dirt, grass and gravel in their way, collecting the blocks along the way.
        * Lumberjack
            * Lumberjack will path around their work radius and chop down trees, replanting saplings in their place when done (if saplings available in inventory).
            * Lumberjacks will break leaves to access logs.
        * Chef
            * Chefs will take any raw meats placed in their inventory to the nearest Furnace -> Campfire in that order, preferring furnaces if campfires are available and falling back to campfire only when there is no furnace present in their work radius.
            * Chefs will place fuel into the furnace if needed (and present in inventory) such as coal, charcoal, wood.
        * Hunter
            * Hunters will seek out and kill passive mobs within their work radius, collecting their loot.
            * This functions the same as setting them to hunt passive mobs, except while on patrol they have a much larger radius in which they hunt.
    * Courier Functionality
        * Companions with Jobs will deliver their inventory (minus their equipped gear) to their assigned chest.
        * If the assigned chest is not chunk loaded, the Companion will alert you that they cannot reach the area.
        * Optional config to enable all assigned chests to chunk load to prevent misdelivery.
* Improved Patrol Logic
    * Pathing Radius now extends to 128 blocks
    * Shift-Clicking now advances radius by 10
    * Companions with Jobs will only work when on patrol
    * Companions will switch to their desired tool when set to patrol, and back to their weapons when taken off patrol.

## v3.49 - v3.60 (Companion AI Overhaul)

### Recruitment
* The taming system is gone. Interacting with an unowned companion recruits them outright.
    * `recruitment.mode` can be set to HANDSHAKE if you would rather sneak to recruit, so you can talk to structure residents without taking them along.
* New players are given one fully equipped Vanguard on first join, with full iron armour, an iron sword and a shield, plus the Command Baton and Perimeter Rod.
    * Use `/companion starter` to claim it in a world that already existed.

### Squads
* Companions can be split into up to eight squads and ordered as units.
* New item: **Command Baton**
    * Right-click ground to send the active squad there. They walk the whole way.
    * Sneak + right-click ground to post them guarding that spot.
    * Right-click a companion to add them to the active squad, sneak to remove.
    * Right-click air to switch squads, sneak + right-click air to cycle their standing order.
* New commands: `/squad` (alias `/sq`) with list, add, clear, name, follow, hold, work, moveto, ward, allfollow and allhold.

### Perimeter defence
* New item: **Perimeter Rod**
    * Click two corners to define your base boundary.
    * Zones extend below and above the marked corners so tunnelling under or flying over is still covered.
    * Right-click a companion to post their whole squad, spread around the perimeter rather than clumped on one corner.

### Pathfinding
* Companions can now path 64 blocks instead of 20, with triple the node budget. This is what lets them walk long distances instead of teleporting.
* Teleporting is now a last resort: only when far away, genuinely unable to find a route, out of combat, off cooldown, and outside your view. Set `navigation.teleportPolicy` to NEVER to disable it entirely.
* Companions that fall behind sprint to catch up rather than blinking to you.
* They step up full blocks instead of stalling, route around lava, fire, magma, campfires, cactus and berry bushes, and prefer shores and bridges over swimming in armour.

### Combat
* **Companions now fight creepers.** They close in, strike, step out of the blast while the fuse burns, then re-engage. A creeper about to reach you gets body-blocked.
* **Ranged companions no longer shoot through you.** Archers, arbalists, mages and firearm users hold fire when you or an ally are in the shot line.
* Wounded companions break off to heal and return, but never while you are fighting nearby, never from a creeper, never when cornered, and never after you go down. When they do break off they back away facing the enemy rather than turning and running.
* Once per fight, a companion below 15% health that is withdrawing takes reduced damage so breaking off is survivable.

### Awareness
* Companions only act on what they have actually sensed. Darkness, rain, storms, cover and **your sneaking** all reduce what they notice.
* They remember a target briefly after losing sight of it and search where it was heading, rather than tracking it through walls or forgetting instantly.
* Squadmates share what they have seen, but only first-hand sightings, so a rumour cannot bounce between them forever.
* Target choice is now a judgement: whatever is hurting you is dealt with before a nearer harmless mob, creepers about to blow jump the queue, wounded targets get finished, and squads spread across targets instead of dogpiling.
* Spotting something and reacting to it are not the same instant. Reaction time scales with Intelligence and is never instant.

### Base defence
* Guarded zones are swept for intruders. Anything a guard can see is dealt with normally.
* Anything that gets inside **unseen** starts a timer, and you are warned if it is still unnoticed after the grace period. Companions cannot be omniscient, so rather than pretending nothing slips past, you are told when it does.
* Guards receive a place to go and look, not a target. They investigate, and only fight once they genuinely see something.
* New squad HUD in the corner showing each squad's name, strength and average health.

### Fixes
* Patrol goals were being registered up to three times per companion, with stale radii.
* Guard, follow and patrol shared one priority band, so which one won depended on registration order rather than what you asked for.
* Every Vanguard was running two melee goals, one inherited from Knight.
* A server-wide handler was constructing an exception for every mob in the world on every tick.
* Enemy callouts were scanning every entity in the loaded level.
* Companions could retaliate against their owner after an accidental hit.
* Weapons placed directly into a companion's hand slot could be lost on reload.




BUGS: 

Lumberjacks spin in place when no trees available to chop.