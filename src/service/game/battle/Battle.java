package src.service.game.battle;

import java.util.ArrayList;

import src.service.entities.Player;
import src.service.entities.attributes.AttackOption;
import src.service.entities.heroes.Hero;
import src.service.entities.items.Item;
import src.service.entities.items.Potion;
import src.service.entities.monsters.Monster;
import src.service.game.PlayerControl;
import src.service.game.StatusDisplay;
import src.util.ItemType;
import src.util.PrintColor;
import src.util.StatsTracker;
import src.util.TextColor;

public class Battle implements PlayerControl, StatusDisplay {

	private static int critHitBonus = 20;

	public Player player;
	public Monster monster;
	public Hero hero;
	public Character lastInput;

	private ArrayList<AttackOption> heroAttacks;
	private AttackOption monsterAttack;
	private int currHeroIdx;

	public Boolean isBattleOver = false;

	private ArrayList<String> statuses;
	private ArrayList<TextColor> statusColors;

	private Boolean gameOver;
	private Boolean didLevelUp;
	private Boolean isBossBattle;

	public Battle(Player player, Monster monster){
		this.monster = monster;
		this.player = player;
		this.hero = player.getFirstHero();
		this.currHeroIdx = 0;

		this.heroAttacks = hero.getAttacksList();
		this.monsterAttack = monster.mainHandAttack();

		this.statuses = new ArrayList<String>();
		this.statusColors = new ArrayList<TextColor>();
		this.gameOver = false;
		this.didLevelUp = false;
		this.isBossBattle = false;
	}
	

	public Boolean battleCycle(){
		StatsTracker.addToStats("Battle cycles progressed", 1);
		
		this.addStatus("Hero health: " + hero.getCurrentHealth(), TextColor.WHITE);
		this.addStatus("Monster health: " + monster.getCurrentHealth(), TextColor.WHITE);
		return true;
		
	}

	public Boolean isBattleOver(){
		return this.isBattleOver;
	}
	public boolean isBossBattle(){
		return this.isBossBattle;
	}

	private Boolean heroAttack(int idx) {
		AttackOption chosenAttackOption = this.heroAttacks.get(idx);
		this.addStatus("Hero: " + chosenAttackOption.getDescription(), TextColor.CYAN);
		Item heroItem = chosenAttackOption.getSourceItem();
		if(heroItem.getItemType() == ItemType.POTION){
			Potion p = (Potion) heroItem;
			if(p.use(this.hero)){
				this.addStatus(this.hero.getName() + " buffs " + p.getPotionType() + " for " + heroItem.getDamage(), TextColor.YELLOW);
			}else {
				this.addStatus("The hero's " + heroItem.getName() + " fails to do anything! Can you use this item?", TextColor.CYAN);
			}
		} else {
			if (heroItem.use()) {
				// Calculate dodge chance
				double dodgeChance = monster.getDodge() / (monster.getDodge() + 250.0);
				double dodgeRoll = Math.random() * 100;
				this.addStatus("Monster Dodge Chance: " + (int)(dodgeChance * 100) + "%", TextColor.PURPLE);
				this.addStatus("Dodge Roll: " + String.format("%.2f", dodgeRoll), TextColor.PURPLE);

				if (dodgeRoll < dodgeChance * 100) {
					this.addStatus("The monster dodged the attack!", TextColor.YELLOW);
					return false;
				}
	
				// Calculate damage
				int baseDamage = chosenAttackOption.getDamage() - monster.getDefense();
				int criticalHit = (int) (Math.random() * critHitBonus); // Random critical hit
				int totalDamage = Math.max(0, baseDamage + criticalHit);
				StatsTracker.addToStats("Damage hero delt", totalDamage);
	
				this.addStatus("", TextColor.PURPLE);
				this.addStatus("Base Damage: Hero Strength + Weapon Damage - Monster Defense = " + baseDamage, TextColor.PURPLE);
				this.addStatus("Critical Hit: Random Value = " + criticalHit, TextColor.PURPLE);
				this.addStatus("Total Damage: Base Damage + Critical Hit = " + totalDamage, TextColor.PURPLE);
				this.addStatus("", TextColor.PURPLE);
	
				this.addStatus("The hero's " + heroItem.getName() + " does " + totalDamage + " damage!", TextColor.CYAN);
				monster.takeDamage(totalDamage);
			} else {
				this.addStatus("The hero's " + heroItem.getName() + " fails to do anything! Can you use this item?", TextColor.CYAN);
			}
	
			if (monster.getCurrentHealth() <= 0) {
				this.isBattleOver = true;
				this.processHeroWin();
				return true;
			}
		}

		
		return false;
	}

	private Boolean monsterAttack() {
		this.addStatus("Monster: " + this.monsterAttack.getDescription(), TextColor.PURPLE);

		// Calculate dodge chance
		double dodgeChance = hero.getDodge() / (hero.getDodge() + 250.0);
		double dodgeRoll = Math.random() * 100;
		this.addStatus("Hero Dodge Chance: " + (int)(dodgeChance * 100) + "%", TextColor.PURPLE);
		this.addStatus("Dodge Roll: " + String.format("%.2f", dodgeRoll), TextColor.PURPLE);

		if (dodgeRoll < dodgeChance * 100) {
			this.addStatus("The hero dodged the attack!", TextColor.YELLOW);
			return false;
		}

		// Calculate damage
		int baseDamage = this.monsterAttack.getDamage() - hero.getDefense();
		int criticalHit = (int) (Math.random() * critHitBonus); // Random critical hit
		int totalDamage = Math.max(0, baseDamage + criticalHit);

		this.addStatus("", TextColor.PURPLE);
		this.addStatus("Base Damage: Monster Strength + Attack Damage - Hero Defense = " + baseDamage, TextColor.PURPLE);
		this.addStatus("Critical Hit: Random Value = " + criticalHit, TextColor.PURPLE);
		this.addStatus("Total Damage: Base Damage + Critical Hit = " + totalDamage, TextColor.PURPLE);
		this.addStatus("", TextColor.PURPLE);

		hero.takeDamage(totalDamage);
		StatsTracker.addToStats("Damage Heros took", totalDamage);
		Item monsterItem = this.monsterAttack.getSourceItem();
		this.addStatus("The monster's " + monsterItem.getName() + " does " + totalDamage + " damage!", TextColor.PURPLE);
		this.addStatus( hero.getName() + "'s remaining health: " + hero.getCurrentHealth(), TextColor.WHITE);

		if (hero.getCurrentHealth() <= 0) {
			this.processMonsterWin();
			return true;
		}
		return false;
	}

	private void processHeroWin(){
		String name = this.hero.getName();
		this.addStatus("------------------------\n", TextColor.WHITE);
		this.addStatus( name + " has defeated the monster!", TextColor.GREEN);
		int rewardedXP = monster.getRewardXP();
		int rewardedGold = monster.getRewardGold();
		this.addStatus( "Your party gains experience and gold! The slayer of the monster gets full rewards, the rest get half rewards!", 
						TextColor.GREEN);

		processPartyWin(this.currHeroIdx, rewardedGold, rewardedXP);


	}

	private void processPartyWin(int currHero, int rewardedGold, int rewardedXP){
		Hero[] currParty = this.player.getParty();
		for(int i = 0; i < currParty.length; i++){
			String name = currParty[i].getName();
			if(currParty[i].getCurrentHealth() < 1){
				currParty[i].healDamage(currParty[i].getCurrentHealth()*-1 + 1);
				this.addStatus(name + " is revieved with 1 hp!", TextColor.RED);
			}
			if(i == currHero){
				this.addStatus( name + " obtains " + rewardedGold + " Gold!", TextColor.GREEN);
				this.addStatus( name + " obtains " + rewardedXP	 + " XP!", TextColor.GREEN);
				if(currParty[i].willLevelUp(rewardedXP)){
									
					this.processHeroLevelUp(name);
				}
				currParty[i].earnGold(rewardedGold);
				currParty[i].gainExperience(rewardedXP);
			} else {
				this.addStatus( name + " obtains " + (rewardedGold / 2) + " Gold!", TextColor.GREEN);
				this.addStatus( name + " obtains " + (rewardedXP / 2)	 + " XP!", TextColor.GREEN);
				if(currParty[i].willLevelUp((rewardedXP / 2))){
									
					this.processHeroLevelUp(name);
				}
				currParty[i].earnGold((rewardedGold / 2));
				currParty[i].gainExperience((rewardedXP / 2));
			}

			
		}
	}

	private void processHeroLevelUp(String name){
		// String name = this.hero.getName();
		this.addStatus( name + " Levelled up!", TextColor.GREEN);
		this.didLevelUp = true;
		this.addStatus( name + " Earns 100 Gold for levelling up!", TextColor.GREEN);
		this.addStatus( name + " levels each of its status up by 5!", TextColor.GREEN);
		this.addStatus( name + " heals 50 HP!", TextColor.GREEN);
		this.addStatus( name + " regains max spell usage!", TextColor.CYAN);

		this.addRainbowStatus( "... you fall deeper into your dream ... ");

	}

	private void processMonsterWin(){
		boolean allHeroesDead = true;
		for (Hero hero : this.player.getParty()) {
			if (hero.getCurrentHealth() > 0) {
				allHeroesDead = false;
				break;
			}
		}
		this.gameOver = allHeroesDead;
		if (allHeroesDead) {
			this.addStatus(this.monster.getName() + " has defeated all the Heroes!", TextColor.RED);
			this.isBattleOver = true;
		} else {
			this.addStatus(this.monster.getName() + " has defeated a Hero!", TextColor.RED);
			if(lastInput != 's'){
				this.switchHeroes();
			}
			
		}
	}

	public Boolean isGameOver(){
		return this.gameOver;
	}
	
	public Boolean getDidLevelUp(){
		return this.didLevelUp;
	}



	@Override
	public Boolean isMoveValid(Character inputtedMove) {
		if(inputtedMove.equals('i') || inputtedMove.equals('b') ||  inputtedMove.equals('s')){
			return true;
		}

		int inputInt;

		try{
			inputInt = Integer.parseInt(inputtedMove.toString());
		} catch (NumberFormatException e){
			this.addStatus("Invalid Move, please try again.", TextColor.RED);
			return false;
		}

		if(inputInt >= 1 && inputInt <= this.heroAttacks.size()){
			return true;
		}
		return false;
	}

	@Override
	public Boolean makeMove(Character inputtedMove) {
		// at the beginning of the turn, update your move list
		this.heroAttacks = hero.getAttacksList();
		this.lastInput = Character.toLowerCase(inputtedMove);
		this.clearStatuses();

		if(!isMoveValid(lastInput)){
			// System.out.println("INVALID MOVE");
			this.addStatus(Character.toString(inputtedMove), TextColor.YELLOW);
			this.addStatus("Invalid Move, please try again.", TextColor.RED);
			return false;
		} 

		processMove(inputtedMove);
		return null;
	}

	public void switchHeroes(){
		int startingIdx = currHeroIdx;
		do {
			currHeroIdx = (currHeroIdx + 1) % player.getParty().length;
		} while (player.getParty()[currHeroIdx].getCurrentHealth() <= 0 && currHeroIdx != startingIdx);

		this.hero = player.getParty()[currHeroIdx];
		this.heroAttacks = this.hero.getAttacksList();
		this.addStatus("Switched to " + hero.getName() + "!", TextColor.CYAN);
	}

	@Override
	public Boolean processMove(Character inputtedMove) {
		if(inputtedMove.equals('i') || inputtedMove.equals('b')){
			return null;
		}
		if(inputtedMove.equals('s')){
			if(monsterAttack()){
				this.addStatus("The monster has defeated the hero :(", TextColor.YELLOW);
			}

			this.switchHeroes();
			return null;

		}

		int inputInt = Integer.parseInt(inputtedMove.toString());

		this.addStatus("Processing attack: " + inputInt, TextColor.CYAN);

		if(this.heroAttack(inputInt - 1)){
			this.addStatus("The hero has defeated the monster!", TextColor.YELLOW);
			return null;
		}
		if(monsterAttack()){
			this.addStatus("The monster has defeated the hero :(", TextColor.YELLOW);
			return null;
		}
		this.battleCycle();
		return null;
	}

	public Hero getHero(){
		return this.hero;
	}

	public ArrayList<AttackOption> getHeroAttacks(){
		return this.heroAttacks;
	}

	@Override
	public void clearStatuses() {
		// TODO Auto-generated method stub
		this.statuses.clear();
		this.statusColors.clear();
	}

	@Override
	public void addStatus(String status, TextColor color) {
		this.statuses.add(status);
		this.statusColors.add(color);
	}

	public void addRainbowStatus(String status) {
		TextColor[] rainbowColors = {
			TextColor.RED, TextColor.ORANGE, TextColor.YELLOW, 
			TextColor.GREEN, TextColor.BLUE, TextColor.PURPLE
		};

		StringBuilder rainbowStatus = new StringBuilder();
		for (int i = 0; i < status.length(); i++) {
			TextColor color = rainbowColors[i % rainbowColors.length];
			rainbowStatus.append(PrintColor.textWithColor(String.valueOf(status.charAt(i)), color));
		}
		this.addStatus("", TextColor.BLACK);
		this.addStatus(rainbowStatus.toString(), TextColor.RESET);
		this.addStatus("", TextColor.BLACK);
	}

	@Override
	public void removeStatus(int index) {
		this.statuses.remove(index);
		this.statusColors.remove(index);
		
	}

	@Override
	public String[] getStatusList() {
		return this.statuses.toArray(new String[0]);
	}

	@Override
	public TextColor[] getStatusColors() {
		return this.statusColors.toArray(new TextColor[0]);
	}


}
