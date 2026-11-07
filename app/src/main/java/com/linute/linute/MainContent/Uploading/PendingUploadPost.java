package com.linute.linute.MainContent.Uploading;

import android.os.Parcel;
import android.os.Parcelable;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by QiFeng on 6/25/16.
 */
public class PendingUploadPost implements Parcelable {

    public static final String PENDING_POST_KEY = "pending_post_key";

    private String mId;
    private String mCollegeId;
    private int mPrivacy;
    private int mIsAnonymousCommentsDisabled;
    private String mTitle;
    private int mLatitude;
    private int mLongitude;
    private int mType;
    private String mImagePath;
    private String mVideoPath;
    private String mOwner;
    private String mUserToken;

    private ArrayList<String> mTrends;
    private ArrayList<String> mPeople;



    private ArrayList<String> mStickers;
    private ArrayList<String> mFilters;
    private String mTrendId;

    public PendingUploadPost(String id,
                             String collegeId,
                             int privacy,
                             int isAnonymousCommentsDisabled,
                             String title,
                             int type,
                             String imagePath,
                             String videoPath,
                             ArrayList<String> stickers,
                             ArrayList<String> filters,
                             String owner,
                             String userToken,
                             String trendId
    ){
        mId = id;
        mCollegeId = collegeId;
        mPrivacy = privacy;
        mIsAnonymousCommentsDisabled = isAnonymousCommentsDisabled;
        mTitle = title;
        mLatitude = 0;
        mLongitude = 0;
        mType = type;
        mImagePath = imagePath;
        mVideoPath = videoPath;
        mStickers = stickers;
        mFilters = filters;
        mOwner = owner;
        mUserToken = userToken;
        mTrendId = trendId;
    }



    /**
     * Saved to a file while the upload waits, because WorkManager's own storage holds at most 10KB
     * and neither the text nor the list of people it's shared with has a limit.
     */
    public String toJson() throws JSONException {
        return new JSONObject()
                .put("id", mId)
                .put("collegeId", mCollegeId)
                .put("privacy", mPrivacy)
                .put("isAnonymousCommentsDisabled", mIsAnonymousCommentsDisabled)
                .put("title", mTitle)
                .put("type", mType)
                .put("imagePath", mImagePath)
                .put("videoPath", mVideoPath)
                .put("owner", mOwner)
                .put("userToken", mUserToken)
                .put("trendId", mTrendId)
                .put("stickers", toArray(mStickers))
                .put("filters", toArray(mFilters))
                .put("people", toArray(mPeople))
                .put("trends", toArray(mTrends))
                .toString();
    }

    public static PendingUploadPost fromJson(String json) throws JSONException {
        JSONObject data = new JSONObject(json);
        PendingUploadPost post = new PendingUploadPost(
                data.optString("id", null),
                data.optString("collegeId", null),
                data.optInt("privacy", 0),
                data.optInt("isAnonymousCommentsDisabled", 0),
                data.optString("title", null),
                data.optInt("type", 0),
                data.optString("imagePath", null),
                data.optString("videoPath", null),
                toList(data.optJSONArray("stickers")),
                toList(data.optJSONArray("filters")),
                data.optString("owner", null),
                data.optString("userToken", null),
                data.optString("trendId", null));
        post.setShareParams(toList(data.optJSONArray("people")), toList(data.optJSONArray("trends")));
        return post;
    }

    private static JSONArray toArray(ArrayList<String> list) {
        return list == null ? new JSONArray() : new JSONArray(list);
    }

    private static ArrayList<String> toList(JSONArray array) throws JSONException {
        ArrayList<String> list = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) list.add(array.getString(i));
        }
        return list;
    }


    public void setCollege(String collegeId){
        mCollegeId = collegeId;
    }


    public ArrayList<String> getPeople() {
        return mPeople;
    }

    public ArrayList<String> getTrends() {
        return mTrends;
    }

    public void setShareParams(ArrayList<String> people, ArrayList<String> trends){
        mPeople = people;
        mTrends = trends;
    }

    public String getId() {
        return mId;
    }

    public String getCollegeId() {
        return mCollegeId;
    }

    public int getPrivacy() {
        return mPrivacy;
    }

    public int getIsAnonymousCommentsDisabled() {
        return mIsAnonymousCommentsDisabled;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getLatitude() {
        return mLatitude;
    }

    public int getLongitude() {
        return mLongitude;
    }

    public int getType() {
        return mType;
    }

    public String getVideoPath() {
        return mVideoPath;
    }

    public String getOwner() {
        return mOwner;
    }

    public String getUserToken() {
        return mUserToken;
    }

    public String getImagePath() {
        return mImagePath;
    }

    public ArrayList<String> getStickers() {
        return mStickers;
    }

    public ArrayList<String> getFilters() {
        return mFilters;
    }

    public String getTrendId() {
        return mTrendId;
    }

    protected PendingUploadPost(Parcel in) {
        mId = in.readString();
        mCollegeId = in.readString();
        mPrivacy = in.readInt();
        mIsAnonymousCommentsDisabled = in.readInt();
        mTitle = in.readString();
        mLatitude = in.readInt();
        mLongitude = in.readInt();
        mType = in.readInt();
        mImagePath = in.readString();
        mVideoPath = in.readString();
        mOwner = in.readString();
        mUserToken = in.readString();
        mPeople = new ArrayList<>();
        in.readStringList(mPeople);
        mTrends = new ArrayList<>();
        in.readStringList(mTrends);
        mStickers = new ArrayList<>();
        in.readStringList(mStickers);
        mFilters = new ArrayList<>();
        in.readStringList(mFilters);
        mTrendId = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeString(mCollegeId);
        dest.writeInt(mPrivacy);
        dest.writeInt(mIsAnonymousCommentsDisabled);
        dest.writeString(mTitle);
        dest.writeInt(mLatitude);
        dest.writeInt(mLongitude);
        dest.writeInt(mType);
        dest.writeString(mImagePath);
        dest.writeString(mVideoPath);
        dest.writeString(mOwner);
        dest.writeString(mUserToken);
        dest.writeStringList(mPeople);
        dest.writeStringList(mTrends);
        dest.writeStringList(mStickers);
        dest.writeStringList(mFilters);
        dest.writeString(mTrendId);
    }

    public static final Creator<PendingUploadPost> CREATOR = new Creator<PendingUploadPost>() {
        @Override
        public PendingUploadPost createFromParcel(Parcel in) {
            return new PendingUploadPost(in);
        }

        @Override
        public PendingUploadPost[] newArray(int size) {
            return new PendingUploadPost[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }



}
