package com.orientechnologies.orient.server.distributed.impl;

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.db.record.ORecordOperation;
import com.orientechnologies.orient.core.delta.ODocumentDelta;
import com.orientechnologies.orient.core.exception.ORecordNotFoundException;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OClassIndexManager;
import com.orientechnologies.orient.core.metadata.schema.OImmutableClass;
import com.orientechnologies.orient.core.metadata.sequence.OSequenceLibraryProxy;
import com.orientechnologies.orient.core.query.live.OLiveQueryHook;
import com.orientechnologies.orient.core.query.live.OLiveQueryHookV2;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ODocumentInternal;
import com.orientechnologies.orient.core.schedule.OScheduledEvent;
import com.orientechnologies.orient.core.tx.OTransactionOptimistic;
import com.orientechnologies.orient.server.distributed.impl.task.OTransactionPhase1Task;

import java.util.*;

public class OTransactionOptimisticDistributed extends OTransactionOptimistic {
  private final Map<ORID, ORecord> createdRecords = new HashMap<>();
  private final Map<ORID, ORecord> updatedRecords = new HashMap<>();
  private final Set<ORID>          deletedRecord  = new HashSet<>();
  private List<ORecordOperation> changes;

  public OTransactionOptimisticDistributed(ODatabaseDocumentInternal database, List<ORecordOperation> changes) {
    super(database);
    this.changes = changes;
  }

  @Override
  public void begin() {
    super.begin();
    for (ORecordOperation change : changes) {
      allEntries.put(change.getRID(), change);
      resolveTracking(change);
    }

  }

  private void resolveTracking(ORecordOperation change) {
    boolean detectedChange = false;
    List<OClassIndexManager.IndexChange> changes = new ArrayList<>();
    if (change.getRecordContainer() instanceof ORecord && change.getRecord() instanceof ODocument) {
      detectedChange = true;
      ODocument rec = (ODocument) change.getRecord();      
      switch (change.getType()) {
      case ORecordOperation.CREATED:
        if (change.getRecord() instanceof ODocument) {
          ODocument doc = (ODocument) change.getRecord();
          OLiveQueryHook.addOp(doc, ORecordOperation.CREATED, database);
          OLiveQueryHookV2.addOp(doc, ORecordOperation.CREATED, database);
          OImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(doc);
          if (clazz != null) {
            OClassIndexManager.processIndexOnCreate(database, rec, changes);
            if (clazz.isFunction()) {
              database.getSharedContext().getFunctionLibrary().createdFunction(doc);
              Orient.instance().getScriptManager().close(database.getName());
            }
            if (clazz.isSequence()) {
              ((OSequenceLibraryProxy) database.getMetadata().getSequenceLibrary()).getDelegate().onSequenceCreated(database, doc);
            }
            if (clazz.isScheduler()) {
              database.getMetadata().getScheduler().scheduleEvent(new OScheduledEvent(doc));
            }
          }
        }
        createdRecords.put(change.getRID().copy(), change.getRecord());
        break;
      case ORecordOperation.UPDATED:
        if (change.getRecord() instanceof ODocument) {          
          ODocument original = null;
          OIdentifiable updateRecord = null;
          if (OTransactionPhase1Task.useDeltasForUpdate){
            updateRecord = change.getRecordContainer();                        
          }
          else{
            updateRecord = change.getRecord();            
          }
          
          original = database.load(updateRecord.getIdentity());
          if (original == null)
            throw new ORecordNotFoundException(updateRecord.getIdentity());
          
          if (OTransactionPhase1Task.useDeltasForUpdate){
            original = original.mergeDelta((ODocumentDelta)updateRecord);
          }
          else{
            original.merge((ODocument)updateRecord, false, false);
          }
          
          ODocument updateDoc = original;
          OLiveQueryHook.addOp(updateDoc, ORecordOperation.UPDATED, database);
          OLiveQueryHookV2.addOp(updateDoc, ORecordOperation.UPDATED, database);
          OImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(updateDoc);
          if (clazz != null) {
            OClassIndexManager.processIndexOnUpdate(database, updateDoc, changes);
            if (clazz.isFunction()) {
              database.getSharedContext().getFunctionLibrary().updatedFunction(updateDoc);
              Orient.instance().getScriptManager().close(database.getName());
            }
            if (clazz.isSequence()) {
              ((OSequenceLibraryProxy) database.getMetadata().getSequenceLibrary()).getDelegate().onSequenceUpdated(database, updateDoc);
            }
          }
        }
        updatedRecords.put(change.getRID(), change.getRecord());
        break;
      case ORecordOperation.DELETED:
        if (change.getRecord() instanceof ODocument) {
          ODocument doc = (ODocument) change.getRecord();
          OImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(doc);
          if (clazz != null) {
            OClassIndexManager.processIndexOnDelete(database, rec, changes);
            if (clazz.isFunction()) {
              database.getSharedContext().getFunctionLibrary().droppedFunction(doc);
              Orient.instance().getScriptManager().close(database.getName());
            }
            if (clazz.isSequence()) {
              ((OSequenceLibraryProxy) database.getMetadata().getSequenceLibrary()).getDelegate().onSequenceDropped(database, doc);
            }
            if (clazz.isScheduler()) {
              final String eventName = doc.field(OScheduledEvent.PROP_NAME);
              database.getSharedContext().getScheduler().removeEventInternal(eventName);
            }
          }
          OLiveQueryHook.addOp(doc, ORecordOperation.DELETED, database);
          OLiveQueryHookV2.addOp(doc, ORecordOperation.DELETED, database);
        }
        deletedRecord.add(change.getRID());
        break;
      case ORecordOperation.LOADED:
        break;
      default:
        break;
      }      
    }
    else if (change.getRecordContainer() instanceof ODocumentDelta){
      detectedChange = true;
      switch (change.getType()) {
      case ORecordOperation.UPDATED:
        
        ODocumentDelta deltaRecord = (ODocumentDelta) change.getRecordContainer();
        ODocument original = database.load(deltaRecord.getIdentity());
        if (original == null)
          throw new ORecordNotFoundException(deltaRecord.getIdentity());
        original = original.mergeDelta(deltaRecord);

        ODocument updateDoc = original;
        OLiveQueryHook.addOp(updateDoc, ORecordOperation.UPDATED, database);
        OLiveQueryHookV2.addOp(updateDoc, ORecordOperation.UPDATED, database);
        OImmutableClass clazz = ODocumentInternal.getImmutableSchemaClass(updateDoc);
        if (clazz != null) {
          OClassIndexManager.processIndexOnUpdate(database, updateDoc, changes);
          if (clazz.isFunction()) {
            database.getSharedContext().getFunctionLibrary().updatedFunction(updateDoc);
            Orient.instance().getScriptManager().close(database.getName());
          }
          if (clazz.isSequence()) {
            ((OSequenceLibraryProxy) database.getMetadata().getSequenceLibrary()).getDelegate().onSequenceUpdated(database, updateDoc);
          }
        }       
        break;
      }
    }
    
    if (detectedChange){
      for (OClassIndexManager.IndexChange indexChange : changes) {
        addIndexEntry(indexChange.index, indexChange.index.getName(), indexChange.operation, indexChange.key, indexChange.value);
      }
    }
  }

  @Override
  public Map<ORID, ORID> getUpdatedRids() {
    return super.getUpdatedRids();
  }

  public Map<ORID, ORecord> getCreatedRecords() {
    return createdRecords;
  }

  public Map<ORID, ORecord> getUpdatedRecords() {
    return updatedRecords;
  }

  public Set<ORID> getDeletedRecord() {
    return deletedRecord;
  }
}
