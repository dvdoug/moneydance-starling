# Moneybot: Window → Show Moneybot Console → Open Script → Run
# Deletes v8-style hidden Starling transfers: isNew, starling FITID, no ol.orig-txn.
# Does not touch confirmed register transfers or ordinary downloads.
#
# 1. Run with DRY_RUN True (default) and read the list.
# 2. Set DRY_RUN False and Run again to delete.

DRY_RUN = True

from com.infinitekind.moneydance.model import ParentTxn, OnlineTxn

book = moneydance.getCurrentAccountBook()
victims = []
for txn in book.getTransactionSet():
    if not isinstance(txn, ParentTxn):
        continue
    fit = txn.getFiTxnId(OnlineTxn.PROTO_TYPE_OFX)
    if fit is None or not str(fit).startswith("starling:"):
        continue
    if txn.isNew() and txn.getOriginalOnlineTxn() is None:
        victims.append(txn)

print "v8-style hidden starling rows: %d" % len(victims)
for t in victims:
    other = t.getOtherSideAccount()
    other_name = other.getFullAccountName() if other is not None else "-"
    print "  %s  %s  %r  other=%s" % (
        t.getDateInt(),
        t.getFiTxnId(OnlineTxn.PROTO_TYPE_OFX),
        t.getDescription(),
        other_name,
    )

if DRY_RUN:
    print "DRY_RUN=True — change to False and run again to delete."
else:
    for t in victims:
        t.deleteItem()
    print "deleted %d" % len(victims)
