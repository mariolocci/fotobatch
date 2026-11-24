package it.mlf.fotobatch.service;

import java.util.TreeSet;

import org.springframework.stereotype.Service;

import it.mlf.fotobatch.model.IndexedItem;


@Service
public class DuplicateService {
	
	private final TreeSet<IndexedItem> indexedItems = new TreeSet<>();
	
	
	TreeSet<IndexedItem> add(IndexedItem item) {
		indexedItems.add(item);
		return indexedItems;
	}
	
	Boolean isDuplicate(IndexedItem item) {
		return indexedItems.contains(item);
	}
	

}
