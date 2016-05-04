package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Span;
import java.nio.ByteBuffer;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Contains trace data that's propagated in-band across requests, sometimes known as Baggage.
 *
 * <p>This implementation is biased towards a fixed-length binary serialization format that doesn't
 * have a way to represent an absent parent (root span). In this serialized form, a root span is
 * when all three ids are the same. Alternatively, you can use {@link #nullableParentId}.
 *
 * <p>Particularly, this includes sampled state, and a portable binary representation. The
 * implementation is a port of {@code com.twitter.finagle.tracing.SpanId}.
 */
@AutoValue
public abstract class SpanId {

  public static final int FLAG_DEBUG = 1 << 0;
  /** When set, we can interpret {@link #FLAG_SAMPLED} as a set value. */
  public static final int FLAG_SAMPLING_SET = 1 << 1;
  public static final int FLAG_SAMPLED = 1 << 2;
  /**
   * When set, we can ignore the value of the {@link #parentId()}
   *
   * <p>While many zipkin systems re-use a trace id as the root span id, we know that some don't.
   * With this flag, we can tell for sure if the span is root as opposed to the convention trace id
   * == span id == parent id.
   */
  public static final int FLAG_IS_ROOT = 1 << 3;

  /**
   * Creates a new span id.
   *
   * @param traceId Trace Id.
   * @param spanId Span Id.
   * @param parentSpanId Nullable parent span id.
   * @deprecated Please use {@link SpanId.Builder}
   */
  @Deprecated
  public static SpanId create(long traceId, long spanId, @Nullable Long parentSpanId) {
    return SpanId.builder().traceId(traceId).spanId(spanId).parentId(parentSpanId).build();
  }

  public static SpanId create(long traceId, long parentId, long spanId, long flags) {
    return new AutoValue_SpanId(
        (parentId == traceId) ? parentId : traceId,
        (parentId == spanId) ? traceId : parentId,
        spanId,
        flags
    );
  }

  /** Deserializes this from a big-endian byte array */
  public static SpanId fromBytes(byte[] bytes) {
    checkNotNull(bytes, "bytes");
    if (bytes.length != 32) {
      throw new IllegalArgumentException("bytes.length " + bytes.length + " != 32");
    }

    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    long spanId = buffer.getLong(0);
    long parentId = buffer.getLong(8);
    long traceId = buffer.getLong(16);
    long flags = buffer.getLong(24);
    return create(traceId, parentId, spanId, flags);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Get Trace id.
   *
   * @return Trace id.
   * @deprecated use {@link #traceId()}
   */
  @Deprecated
  public long getTraceId() {
    return traceId();
  }

  /**
   * Get span id.
   *
   * @return span id.
   * @deprecated use {@link #spanId()}
   */
  @Deprecated
  public long getSpanId() {
    return spanId();
  }

  /**
   * Get parent span id.
   *
   * @return Parent span id. Can be <code>null</code>.
   * @deprecated use {@link #nullableParentId()}
   */
  @Deprecated
  @Nullable
  public Long getParentSpanId() {
    return nullableParentId();
  }

  /**
   * Unique 8-byte identifier for a trace, set on all spans within it.
   */
  public abstract long traceId();

  /**
   * The parent's {@link #spanId} or {@link #spanId} if this the root span in a trace.
   */
  public abstract long parentId();

  /** Returns null when this is a root span. */
  @Nullable
  public Long nullableParentId() {
    return root() ? null : parentId();
  }

  /**
   * Unique 8-byte identifier of this span within a trace.
   *
   * <p>A span is uniquely identified in storage by ({@linkplain #traceId}, {@code #id}).
   */
  public abstract long spanId();

  /** Returns true if this is the root span. */
  public final boolean root() {
    return (flags() & FLAG_IS_ROOT) == FLAG_IS_ROOT ||
        parentId() == traceId() && parentId() == spanId();
  }

  /**
   * True is a request to store this span even if it overrides sampling policy. Implies {@link
   * #sampled()}.
   */
  public final boolean debug() {
    return (flags() & FLAG_DEBUG) == FLAG_DEBUG;
  }

  /**
   * Should we sample this request or not? True means sample, false means don't, null means we defer
   * decision to someone further down in the stack.
   */
  @Nullable
  public final Boolean sampled() {
    if (debug()) return true;
    return (flags() & FLAG_SAMPLING_SET) == FLAG_SAMPLING_SET
        ? (flags() & FLAG_SAMPLED) == FLAG_SAMPLED
        : null;
  }

  /** Raw flags encoded in {@link #bytes()} */
  public abstract long flags();

  /** Serializes this into a big-endian byte array */
  public byte[] bytes() {
    byte[] result = new byte[32];
    ByteBuffer buffer = ByteBuffer.wrap(result);
    buffer.putLong(0, spanId());
    buffer.putLong(8, parentId());
    buffer.putLong(16, traceId());
    buffer.putLong(24, flags());
    return result;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public String toString() {
    return String.format("%016x.%016x<:%016x", traceId(), spanId(), parentId());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SpanId) {
      SpanId that = (SpanId) o;
      return (this.traceId() == that.traceId())
          && (this.parentId() == that.parentId())
          && (this.spanId() == that.spanId());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (traceId() >>> 32) ^ traceId();
    h *= 1000003;
    h ^= (parentId() >>> 32) ^ parentId();
    h *= 1000003;
    h ^= (spanId() >>> 32) ^ spanId();
    return h;
  }

  /** Preferred way to create spans, as it properly deals with the parent id */
  public Span toSpan() {
    Span result = new Span();
    result.setId(spanId());
    result.setTrace_id(traceId());
    result.setParent_id(nullableParentId());
    if (debug()) result.setDebug(debug());
    return result;
  }

  public static final class Builder {
    Long traceId;
    Long parentId;
    Long spanId;
    long flags;

    Builder() {
    }

    Builder(SpanId source) {
      this.traceId = source.traceId();
      this.parentId = source.nullableParentId();
      this.spanId = source.spanId();
      this.flags = source.flags();
    }

    /** @see SpanId#traceId() */
    public Builder traceId(long traceId) {
      this.traceId = traceId;
      return this;
    }

    /**
     * If your trace ids are not span ids, you must call this method to indicate absent parent.
     *
     * @see SpanId#nullableParentId()
     */
    public Builder parentId(@Nullable Long parentId) {
      if (parentId == null) {
        this.flags |= FLAG_IS_ROOT;
      } else {
        this.flags &= ~FLAG_IS_ROOT;
      }
      this.parentId = parentId;
      return this;
    }

    /** @see SpanId#spanId() */
    public Builder spanId(long spanId) {
      this.spanId = spanId;
      return this;
    }

    /** @see SpanId#flags() */
    public Builder flags(long flags) {
      this.flags = flags;
      return this;
    }

    /** @see SpanId#debug() */
    public Builder debug(boolean debug) {
      if (debug) {
        this.flags |= FLAG_DEBUG;
      } else {
        this.flags &= ~FLAG_DEBUG;
      }
      return this;
    }

    /** @see SpanId#sampled */
    public Builder sampled(@Nullable Boolean sampled) {
      if (sampled != null) {
        this.flags |= FLAG_SAMPLING_SET;
        if (sampled) {
          this.flags |= FLAG_SAMPLED;
        } else {
          this.flags &= ~FLAG_SAMPLED;
        }
      } else {
        this.flags &= ~FLAG_SAMPLING_SET;
      }
      return this;
    }

    public SpanId build() {
      long traceId = this.traceId != null ? this.traceId : checkNotNull(spanId, "spanId");
      long parentId = this.parentId != null ? this.parentId : traceId;
      return SpanId.create(traceId, parentId, checkNotNull(spanId, "spanId"), flags);
    }
  }

  SpanId() {
  }
}
