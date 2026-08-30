# Moneybot: Window → Show Moneybot Console → Open Script → Run
# Read-only. Lists Starling FITIDs, hidden isNew rows, and download queues.

from com.infinitekind.moneydance.model import ParentTxn, OnlineTxn

book = moneydance.getCurrentAccountBook()
root = book.getRootAccount()


def walk(acct, accs):
    accs.append(acct)
    for i in range(acct.getSubAccountCount()):
        walk(acct.getSubAccount(i), accs)
    return accs


print "=== Downloaded (OnlineTxn) lists ==="
dl_total = 0
for acct in walk(root, []):
    dl = acct.getDownloadedTxns()
    if dl is None:
        continue
    n = dl.getTxnCount()
    if n == 0:
        continue
    dl_total += n
    print "%s  downloads=%d" % (acct.getFullAccountName(), n)
    for i in range(min(n, 30)):
        ot = dl.getTxn(i)
        print "  %s  amt=%s  name=%s" % (ot.getFITxnId(), ot.getAmount(), ot.getName())
    if n > 30:
        print "  ... %d more" % (n - 30)
if dl_total == 0:
    print "(none)"

print
print "=== Register ParentTxns with starling FITID ==="
count = 0
hidden = 0
for txn in book.getTransactionSet():
    if not isinstance(txn, ParentTxn):
        continue
    fit = txn.getFiTxnId(OnlineTxn.PROTO_TYPE_OFX)
    if fit is None or not str(fit).startswith("starling:"):
        continue
    count += 1
    orig = txn.getOriginalOnlineTxn()
    other = txn.getOtherSideAccount()
    other_name = other.getFullAccountName() if other is not None else "-"
    flag = ""
    if txn.isNew() and orig is None:
        hidden += 1
        flag = "  [likely hidden: isNew, no ol.orig-txn]"
    print "%s  %s  isNew=%s  orig=%s  desc=%r  val=%s  other=%s%s" % (
        txn.getDateInt(),
        fit,
        txn.isNew(),
        orig is not None,
        txn.getDescription(),
        txn.getValue(),
        other_name,
        flag,
    )

print
print "starling register rows: %d" % count
print "isNew without download original (v8-style hidden): %d" % hidden
print "download-list rows: %d" % dl_total
