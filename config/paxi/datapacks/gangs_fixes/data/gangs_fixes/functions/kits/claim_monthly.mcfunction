execute if entity @s[nbt={Inventory:[{Slot:22b}]}] run tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Claim failed: You need at least 14 empty inventory slots to claim Monthly Kit!","color":"red"}]
execute unless entity @s[nbt={Inventory:[{Slot:22b}]}] run function gangs_fixes:kits/do_claim_m
