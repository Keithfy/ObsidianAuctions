package com.flobi.floauction;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import me.virustotal.floauction.utility.CArrayList;

import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentWrapper;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.flobi.floauction.utilities.Items;

/**
 * Structure to hold and process the items being auctioned.
 * 
 * @author Joshua "flobi" Hatfield
 */
public class AuctionLot implements java.io.Serializable {
	private static final long serialVersionUID = -1764290458703647129L;
	private String ownerName;
	private int quantity = 0;
	private int lotTypeId;
	private short lotDurability;
	private Map<Integer, Integer> lotEnchantments;
	private Map<Integer, Integer> storedEnchantments;
	private int sourceStackQuantity = 0;
	private String displayName = "";
	private String bookAuthor = "";
	private String bookTitle = "";
	private String[] bookPages = null;
	private Integer repairCost = null;
	private String headOwner = null;
	private Integer power = 0;
	private FireworkEffect[] effects = null;
	private String[] lore = null;
//	private Map<String, Object> itemSerialized = null;
	private String itemSerialized = null;
	
	/**
	 * Constructor that sets owner and lot type.
	 * 
	 * @param lotType
	 * @param lotOwner
	 */
	public AuctionLot(ItemStack lotType, String lotOwner) {
		// Lots can only have one type of item per lot.
		ownerName = lotOwner;
		setLotType(lotType);
	}
	
	/**
	 * Adds items to this lot by removing them from a player.
	 * 
	 * @param addQuantity amount to move
	 * @param removeFromOwner player to take items from
	 * @return whether the items were moved
	 */
	public boolean addItems(int addQuantity, boolean removeFromOwner) {
		if (removeFromOwner) {
			if (!Items.hasAmount(ownerName, addQuantity, getTypeStack())) {
				return false;
			}
			Items.remove(ownerName, addQuantity, getTypeStack());
		}
		quantity += addQuantity;
		return true;
	}
	
	/**
	 * Public alias for giveLot(String playerName) used when we happen to be giving the lot to an auction winner or authorized confiscator.
	 * 
	 * @param winnerName who receives the items
	 */
	public void winLot(String winnerName) {
		giveLot(winnerName);
	}
	
	/**
	 * Cancels the lot by giving the items to the lots original owner.
	 */
	public void cancelLot() {
		giveLot(ownerName);
	}
	
	/**
	 * Gives the items to a player, drops excess on ground or saves all of it to orphanage if the player is offline.
	 * 
	 * @param playerName who receives the items
	 */
	private void giveLot(String playerName) {
		ownerName = playerName;
		if (quantity == 0) return;
		ItemStack lotTypeLock = getTypeStack();
		Player player = Bukkit.getPlayer(playerName);
		
		int maxStackSize = lotTypeLock.getType().getMaxStackSize();
		if (player != null && player.isOnline()) {
			int amountToGive = 0;
			if (Items.hasSpace(player, quantity, lotTypeLock)) {
				amountToGive = quantity;
			} else {
				amountToGive = Items.getSpaceForItem(player, lotTypeLock);
			}
			// Give whatever items space permits at this time.
			ItemStack typeStack = getTypeStack();
			if (amountToGive > 0) {
				FloAuction.getMessageManager().sendPlayerMessage(new CArrayList<String>("lot-give"), playerName, (AuctionScope) null);
			}
			while (amountToGive > 0) {
				ItemStack givingItems = lotTypeLock.clone();
				givingItems.setAmount(Math.min(maxStackSize, amountToGive));
				quantity -= givingItems.getAmount();
				
//				player.getInventory().addItem();
				Items.saferItemGive(player.getInventory(), givingItems);
				
				amountToGive -= maxStackSize;
			}
			if (quantity > 0) {
				// Drop items at player's feet.
				
				// Move items to drop lot.
				while (quantity > 0) {
					ItemStack cloneStack = typeStack.clone();
					cloneStack.setAmount(Math.min(quantity, Items.getMaxStackSize(typeStack)));
					quantity -= cloneStack.getAmount();
					
					// Drop lot.
					Item drop = player.getWorld().dropItemNaturally(player.getLocation(), cloneStack);
					drop.setItemStack(cloneStack);
				}
				FloAuction.getMessageManager().sendPlayerMessage(new CArrayList<String>("lot-drop"), playerName, (AuctionScope) null);
			}
		} else {
			// Player is offline, queue lot for give on login.
			// Create orphaned lot to try to give when inventory clears up.
			final AuctionLot orphanLot = new AuctionLot(lotTypeLock, playerName);
			
			// Move items to orphan lot
			orphanLot.addItems(quantity, false);
			quantity = 0;
			
			// Queue for distribution on space availability.
			FloAuction.saveOrphanLot(orphanLot);
		}
	}
	
	/**
	 * Gets a stack of a single item having the properties of all the items in this lot.
	 * 
	 * @return item stack of one item
	 */
	@SuppressWarnings("deprecation")
	public ItemStack getTypeStack() {
		ItemStack lotTypeLock = null;
		if (this.itemSerialized != null) {
//			lotTypeLock = ItemStack.deserialize(this.itemSerialized);
			FileConfiguration tmpconfig = new YamlConfiguration();
			try {
				tmpconfig.loadFromString(this.itemSerialized);
				if (tmpconfig.isItemStack("itemstack")) {
					return tmpconfig.getItemStack("itemstack");
				}
			} catch (InvalidConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// The rest of this remains for backward compatibility.
		lotTypeLock = new ItemStack(lotTypeId, 1, lotDurability);
		
		for (Entry<Integer, Integer> enchantment : lotEnchantments.entrySet()) {
			lotTypeLock.addUnsafeEnchantment(new EnchantmentWrapper(enchantment.getKey()), enchantment.getValue());
		}
		for (Entry<Integer, Integer> enchantment : storedEnchantments.entrySet()) {
			Items.addStoredEnchantment(lotTypeLock, enchantment.getKey(), enchantment.getValue(), true);
		}
		lotTypeLock.setAmount(sourceStackQuantity);
		Items.setDisplayName(lotTypeLock, displayName);
		Items.setBookAuthor(lotTypeLock, bookAuthor);
		Items.setBookTitle(lotTypeLock, bookTitle);
		Items.setBookPages(lotTypeLock, bookPages);
		Items.setRepairCost(lotTypeLock, repairCost);
		Items.setHeadOwner(lotTypeLock, headOwner);
		Items.setFireworkPower(lotTypeLock, power);
		Items.setFireworkEffects(lotTypeLock, effects);
		Items.setLore(lotTypeLock, lore);
		return lotTypeLock;
	}
	
	/**
	 * Sets the items in this lot to have the properties of the referenced item stack.
	 * 
	 * @param lotType
	 */
	@SuppressWarnings("deprecation")
	private void setLotType(ItemStack lotType) {
//		this.itemSerialized = lotType.serialize();
		FileConfiguration tmpconfig = new YamlConfiguration();
		tmpconfig.set("itemstack", lotType);
		itemSerialized = tmpconfig.saveToString();

		// The rest of this remains for backward compatibility.
		lotTypeId = lotType.getTypeId();
		lotDurability = lotType.getDurability();
		sourceStackQuantity = lotType.getAmount();
		lotEnchantments = new HashMap<Integer, Integer>();
		storedEnchantments = new HashMap<Integer, Integer>();
		Map<Enchantment, Integer> enchantmentList = lotType.getEnchantments();
		for (Entry<Enchantment, Integer> enchantment : enchantmentList.entrySet()) {
			lotEnchantments.put(enchantment.getKey().getId(), enchantment.getValue());
		}
		enchantmentList = Items.getStoredEnchantments(lotType);
		if (enchantmentList != null) for (Entry<Enchantment, Integer> enchantment : enchantmentList.entrySet()) {
			storedEnchantments.put(enchantment.getKey().getId(), enchantment.getValue());
		}
		displayName = Items.getDisplayName(lotType);
		bookAuthor = Items.getBookAuthor(lotType);
		bookTitle = Items.getBookTitle(lotType);
		bookPages = Items.getBookPages(lotType);
		repairCost = Items.getRepairCost(lotType);
		headOwner = Items.getHeadOwner(lotType);
		power = Items.getFireworkPower(lotType);
		effects = Items.getFireworkEffects(lotType);
		lore = Items.getLore(lotType);
	}
	
	/**
	 * Gets the name of the owner of this lot.
	 * 
	 * @return name of lot owner
	 */
	public String getOwner() {
		return ownerName;
	}
	
	/**
	 * Gets the quantity of items in this lot.
	 * 
	 * @return quantity of items in lot
	 */
	public int getQuantity() {
		return quantity;
	}
}
