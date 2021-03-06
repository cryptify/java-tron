/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.tron.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.ECKey.ECDSASignature;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.utils.MerkleTree;
import org.tron.core.exception.BadItemException;
import org.tron.core.exception.ValidateSignatureException;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.BlockHeader;
import org.tron.protos.Protocol.Transaction;

@Slf4j
public class BlockCapsule implements ProtoCapsule<Block> {

  public static class BlockId extends Sha256Hash {

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || (getClass() != o.getClass() && !(o instanceof Sha256Hash))) {
        return false;
      }
      return Arrays.equals(getBytes(), ((Sha256Hash) o).getBytes());
    }

    public String getString() {
      return "Num: " + num + ",ID:" + super.toString();
    }

    @Override
    public String toString() {
      return super.toString();
    }

    @Override
    public int hashCode() {
      return super.hashCode();
    }

    @Override
    public int compareTo(Sha256Hash other) {
      if (other.getClass().equals(BlockId.class)) {
        long otherNum = ((BlockId) other).getNum();
        if (num > otherNum) {
          return 1;
        } else if (otherNum < num) {
          return -1;
        }
      }
      return super.compareTo(other);
    }

    private long num;

    public BlockId() {
      super(Sha256Hash.ZERO_HASH.getBytes());
      num = 0;
    }

    /**
     * Use {@link #wrap(byte[])} instead.
     */
    public BlockId(Sha256Hash hash, long num) {
      super(hash.getBytes());
      this.num = num;
    }

    public BlockId(byte[] hash, long num) {
      super(hash);
      this.num = num;
    }

    public BlockId(ByteString hash, long num) {
      super(hash.toByteArray());
      this.num = num;
    }

    public long getNum() {
      return num;
    }
  }

  private BlockId blockId = new BlockId(Sha256Hash.ZERO_HASH, 0);

  private Block block;
  public boolean generatedByMyself = false;

  public BlockCapsule(long number, Sha256Hash hash, long when, ByteString witnessAddress) {
    // blockheader raw
    BlockHeader.raw.Builder blockHeaderRawBuild = BlockHeader.raw.newBuilder();
    BlockHeader.raw blockHeaderRaw = blockHeaderRawBuild
        .setNumber(number)
        .setParentHash(hash.getByteString())
        .setTimestamp(when)
        .setWitnessAddress(witnessAddress).build();

    // block header
    BlockHeader.Builder blockHeaderBuild = BlockHeader.newBuilder();
    BlockHeader blockHeader = blockHeaderBuild.setRawData(blockHeaderRaw).build();

    // block
    Block.Builder blockBuild = Block.newBuilder();
    this.block = blockBuild.setBlockHeader(blockHeader).build();
  }

  public BlockCapsule(long number, ByteString hash, long when, ByteString witnessAddress) {
    // blockheader raw
    BlockHeader.raw.Builder blockHeaderRawBuild = BlockHeader.raw.newBuilder();
    BlockHeader.raw blockHeaderRaw = blockHeaderRawBuild
        .setNumber(number)
        .setParentHash(hash)
        .setTimestamp(when)
        .setWitnessAddress(witnessAddress).build();

    // block header
    BlockHeader.Builder blockHeaderBuild = BlockHeader.newBuilder();
    BlockHeader blockHeader = blockHeaderBuild.setRawData(blockHeaderRaw).build();

    // block
    Block.Builder blockBuild = Block.newBuilder();
    this.block = blockBuild.setBlockHeader(blockHeader).build();
  }

  public BlockCapsule(long timestamp, ByteString parentHash, long number,
      List<Transaction> transactionList) {
    // blockheader raw
    BlockHeader.raw.Builder blockHeaderRawBuild = BlockHeader.raw.newBuilder();
    BlockHeader.raw blockHeaderRaw = blockHeaderRawBuild
        .setTimestamp(timestamp)
        .setParentHash(parentHash)
        .setNumber(number)
        .build();

    // block header
    BlockHeader.Builder blockHeaderBuild = BlockHeader.newBuilder();
    BlockHeader blockHeader = blockHeaderBuild.setRawData(blockHeaderRaw).build();

    // block
    Block.Builder blockBuild = Block.newBuilder();
    transactionList.forEach(trx -> blockBuild.addTransactions(trx));
    this.block = blockBuild.setBlockHeader(blockHeader).build();

  }

  public void addTransaction(TransactionCapsule pendingTrx) {
    this.block = this.block.toBuilder().addTransactions(pendingTrx.getInstance()).build();
  }

  public List<TransactionCapsule> getTransactions() {
    return this.block.getTransactionsList().stream()
        .map(trx -> new TransactionCapsule(trx))
        .collect(Collectors.toList());
  }

  public void sign(byte[] privateKey) {
    // TODO private_key == null
    ECKey ecKey = ECKey.fromPrivate(privateKey);
    ECDSASignature signature = ecKey.sign(getRawHash().getBytes());
    ByteString sig = ByteString.copyFrom(signature.toByteArray());

    BlockHeader blockHeader = this.block.getBlockHeader().toBuilder().setWitnessSignature(sig)
        .build();

    this.block = this.block.toBuilder().setBlockHeader(blockHeader).build();
  }

  private Sha256Hash getRawHash() {
    return Sha256Hash.of(this.block.getBlockHeader().getRawData().toByteArray());
  }

  public boolean validateSignature() throws ValidateSignatureException {
    try {
      return Arrays
          .equals(ECKey.signatureToAddress(getRawHash().getBytes(),
              TransactionCapsule
                  .getBase64FromByteString(block.getBlockHeader().getWitnessSignature())),
              block.getBlockHeader().getRawData().getWitnessAddress().toByteArray());
    } catch (SignatureException e) {
      throw new ValidateSignatureException(e.getMessage());
    }
  }

  public BlockId getBlockId() {
    if (blockId.equals(Sha256Hash.ZERO_HASH)) {
      blockId = new BlockId(Sha256Hash.of(this.block.getBlockHeader().toByteArray()), getNum());
    }
    return blockId;
  }

  public Sha256Hash calcMerkleRoot() {
    List<Transaction> transactionsList = this.block.getTransactionsList();

    if (CollectionUtils.isEmpty(transactionsList)) {
      return Sha256Hash.ZERO_HASH;
    }

    Vector<Sha256Hash> ids = transactionsList.stream()
        .map(TransactionCapsule::new)
        .map(TransactionCapsule::getHash)
        .collect(Collectors.toCollection(Vector::new));

    return MerkleTree.getInstance().createTree(ids).getRoot().getHash();
  }

  public void setMerkleRoot() {
    BlockHeader.raw blockHeaderRaw =
        this.block.getBlockHeader().getRawData().toBuilder()
            .setTxTrieRoot(calcMerkleRoot().getByteString()).build();

    this.block = this.block.toBuilder().setBlockHeader(
        this.block.getBlockHeader().toBuilder().setRawData(blockHeaderRaw)).build();
  }

  public Sha256Hash getMerkleRoot() {
    return Sha256Hash.wrap(this.block.getBlockHeader().getRawData().getTxTrieRoot());
  }

  public ByteString getWitnessAddress() {
    return this.block.getBlockHeader().getRawData().getWitnessAddress();
  }


  public BlockCapsule(Block block) {
    this.block = block;
  }

  public BlockCapsule(byte[] data) throws BadItemException {
    try {
      this.block = Block.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      throw new BadItemException();
    }
  }

  @Override
  public byte[] getData() {
    return this.block.toByteArray();
  }

  @Override
  public Block getInstance() {
    return this.block;
  }

  public Sha256Hash getParentHash() {
    return Sha256Hash.wrap(this.block.getBlockHeader().getRawData().getParentHash());
  }

  public ByteString getParentHashStr() {
    return this.block.getBlockHeader().getRawData().getParentHash();
  }

  public long getNum() {
    return this.block.getBlockHeader().getRawData().getNumber();
  }

  public long getTimeStamp() {
    return this.block.getBlockHeader().getRawData().getTimestamp();
  }

  @Override
  public String toString() {
    return "BlockCapsule{" +
        "blockId=" + blockId +
        ", num=" + getNum() +
        ", parentId=" + getParentHash() +
        ", generatedByMyself=" + generatedByMyself +
        '}';
  }
}
