package dev.probe.notif;

import android.os.Parcel;
import android.os.Parcelable;

/** Parcelable propio: el host no tiene esta clase, por lo que no puede desmarshalarlo. */
public class Custom implements Parcelable {
    public final String v;
    public Custom(String v) { this.v = v; }
    protected Custom(Parcel p) { v = p.readString(); }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel d, int f) { d.writeString(v); }
    public static final Creator<Custom> CREATOR = new Creator<Custom>() {
        @Override public Custom createFromParcel(Parcel p) { return new Custom(p); }
        @Override public Custom[] newArray(int n) { return new Custom[n]; }
    };
}
