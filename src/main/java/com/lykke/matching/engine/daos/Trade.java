package com.lykke.matching.engine.daos;

import com.microsoft.azure.storage.table.TableServiceEntity;

import java.util.Date;

public class Trade extends TableServiceEntity {
    //partition key: Client id
    //row key: generated uid

    String assetId;
    Date dateTime = new Date();
    String limitOrderId;
    String marketOrderId;
    Double volume;

    public Trade() {
    }

    public Trade(String partitionKey, String rowKey, String assetId, Date dateTime, String limitOrderId, String marketOrderId, Double volume) {
        super(partitionKey, rowKey);
        this.assetId = assetId;
        this.dateTime = dateTime;
        this.limitOrderId = limitOrderId;
        this.marketOrderId = marketOrderId;
        this.volume = volume;
    }

    public String getClientId() {
        return partitionKey;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public Date getDateTime() {
        return dateTime;
    }

    public void setDateTime(Date dateTime) {
        this.dateTime = dateTime;
    }

    public String getLimitOrderId() {
        return limitOrderId;
    }

    public void setLimitOrderId(String limitOrderId) {
        this.limitOrderId = limitOrderId;
    }

    public String getMarketOrderId() {
        return marketOrderId;
    }

    public void setMarketOrderId(String marketOrderId) {
        this.marketOrderId = marketOrderId;
    }

    public Double getVolume() {
        return volume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }

    @Override
    public String toString() {
        return "Trade(" +
                "clientId='" + partitionKey + '\'' +
                "uid='" + rowKey + '\'' +
                "assetId='" + assetId + '\'' +
                ", dateTime=" + dateTime +
                ", limitOrderId='" + limitOrderId + '\'' +
                ", marketOrderId='" + marketOrderId + '\'' +
                ", volume=" + volume +
                ')';
    }
}