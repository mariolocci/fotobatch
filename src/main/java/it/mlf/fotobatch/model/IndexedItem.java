package it.mlf.fotobatch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
@AllArgsConstructor
public class IndexedItem implements Comparable<IndexedItem> {
	@Override
	public int compareTo(IndexedItem other) {
		return this.metadataHash.compareTo(other.metadataHash);
	}
	public String filePath;
	public String metadataHash;

}
