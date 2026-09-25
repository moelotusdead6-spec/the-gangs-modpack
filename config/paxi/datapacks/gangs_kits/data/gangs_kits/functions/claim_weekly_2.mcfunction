execute if score @s gangs_claimed_w2 matches 1 run tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"You have already claimed the Week 2 Kit!","color":"red"}]
execute unless score @s gangs_claimed_w2 matches 1 run function gangs_kits:count_occupied_slots
execute unless score @s gangs_claimed_w2 matches 1 if score #occupied gangs_temp matches 27.. run tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Claim failed: You need at least 10 empty inventory slots to claim Week 2 Kit!","color":"red"}]
execute unless score @s gangs_claimed_w2 matches 1 if score #occupied gangs_temp matches 27.. run kits resetusage @s 2_weekly_2
execute unless score @s gangs_claimed_w2 matches 1 if score #occupied gangs_temp matches ..26 run function gangs_kits:do_claim_w2
