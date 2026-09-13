execute if entity @s[nbt={Inventory:[{Slot:26b}]}] run tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Claim failed: You need at least 10 empty inventory slots to claim Week 1 Kit!","color":"red"}]
execute unless entity @s[nbt={Inventory:[{Slot:26b}]}] run function gangs_fixes:kits/do_claim_w1
