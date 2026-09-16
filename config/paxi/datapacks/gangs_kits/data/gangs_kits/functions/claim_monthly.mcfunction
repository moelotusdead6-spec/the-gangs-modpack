execute if score @s gangs_claimed_m matches 1 run tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"You have already claimed the Monthly Kit!","color":"red"}]
execute unless score @s gangs_claimed_m matches 1 run function gangs_kits:count_occupied_slots
execute unless score @s gangs_claimed_m matches 1 if score #occupied gangs_temp matches 23.. run tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Claim failed: You need at least 14 empty inventory slots to claim Monthly Kit!","color":"red"}]
execute unless score @s gangs_claimed_m matches 1 if score #occupied gangs_temp matches ..22 run function gangs_kits:do_claim_m
