package com.example.onlycorn.activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.onlycorn.R;
import com.example.onlycorn.models.Post;
import com.example.onlycorn.models.User;
import com.example.onlycorn.utils.FirebaseUtils;
import com.example.onlycorn.utils.Pop;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class AddPostActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int STORAGE_REQUEST_CODE = 200;
    private static final int IMAGE_PICK_REQUEST_CODE = 300;

    private EditText captionEt, descriptionEt;
    private ImageView postImage;
    private Button uploadButton;

    private Uri imageUri;
    private ProgressDialog pd;
    private boolean editPost;
    private String postId;
    private Post post;
    private User user;

    String[] cameraPermissions;
    String[] storagePermissions;

    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_post);
        initViews();

        FirebaseUser userFb = FirebaseUtils.getUserAuth();
        FirebaseUtils.getDocumentRef(User.COLLECTION, userFb.getUid()).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        user = documentSnapshot.toObject(User.class);
                    }
                });

        editPost = getIntent().getStringExtra("key") != null && getIntent().getStringExtra("key").equals("editPost");
        if (editPost) {
            uploadButton.setText("Update");
            postId = getIntent().getStringExtra("postId");
            loadPostData(postId);
        } else {
            uploadButton.setText("Upload");
        }

        cameraPermissions = new String[] {Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_VIDEO};
        storagePermissions = new String[] {Manifest.permission.READ_MEDIA_VIDEO};

        pd = new ProgressDialog(this);

    }

    private void initViews() {
        captionEt = findViewById(R.id.captionEt);
        descriptionEt = findViewById(R.id.descriptionEt);
        postImage = findViewById(R.id.postImage);
        uploadButton = findViewById(R.id.uploadButton);

        postImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImagePickDialog();
            }
        });

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String caption = captionEt.getText().toString().trim();
                String description = descriptionEt.getText().toString().trim();

                if (TextUtils.isEmpty(caption)) {
                    Pop.pop(AddPostActivity.this, "Enter caption...");
                    return;
                }

                if (editPost) {
                    beginUpdate(caption, description, postId);
                } else {
                    uploadData(caption, description);
                }
            }
        });
    }

    private void beginUpdate(String caption, String description, String postId) {
        pd.setTitle("Updating Post...");
        pd.show();

        if (!post.getImage().equals("noImage")) {
            updateWasWithImage();
        } else if (postImage.getDrawable() != null) {
            updateWithNowImage();
        } else {
            updateWithoutImage();
        }
    }

    private void updateWithoutImage() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        Post postDb = new Post(postId, captionEt.getText().toString(),
                descriptionEt.getText().toString(), "noImage", timestamp,
                post.getLikes(), post.getComments(), user.getUserId(),
                user.getUsername(), user.getImage());
        DocumentReference ref = FirebaseUtils.getDocumentRef(Post.COLLECTION, postId);
        ref.set(postDb)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        pd.dismiss();
                        Pop.pop(AddPostActivity.this, "Updated...");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        pd.dismiss();
                        Pop.pop(AddPostActivity.this, e.getMessage());
                    }
                });
    }

    private void updateWithNowImage() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String filePathAndName = "Posts/" + "post_" + timestamp;

        Bitmap bitmap = ((BitmapDrawable)postImage.getDrawable()).getBitmap();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] data = baos.toByteArray();

        StorageReference ref = FirebaseUtils.getStorageRef(filePathAndName);
        ref.putBytes(data)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();
                        while(!uriTask.isSuccessful());

                        String downloadUri = uriTask.getResult().toString();
                        if (uriTask.isSuccessful()) {
                            Post postDb = new Post(postId, captionEt.getText().toString(),
                                    descriptionEt.getText().toString(), downloadUri, timestamp,
                                    post.getLikes(), post.getComments(), user.getUserId(),
                                    user.getUsername(), user.getImage());
                            DocumentReference ref = FirebaseUtils.getDocumentRef(Post.COLLECTION, postId);
                            ref.set(postDb)
                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void unused) {
                                            pd.dismiss();
                                            Pop.pop(AddPostActivity.this, "Updated...");
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            pd.dismiss();
                                            Pop.pop(AddPostActivity.this, e.getMessage());
                                        }
                                    });
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        pd.dismiss();
                        Pop.pop(AddPostActivity.this, e.getMessage());
                    }
                });

    }

    private void updateWasWithImage() {
        StorageReference imageRef = FirebaseUtils.getStorageRefFromUrl(post.getImage());
        imageRef.delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        String timestamp = String.valueOf(System.currentTimeMillis());
                        String filePathAndName = "Posts/" + "post_" + timestamp;

                        Bitmap bitmap = ((BitmapDrawable)postImage.getDrawable()).getBitmap();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                        byte[] data = baos.toByteArray();

                        StorageReference ref = FirebaseUtils.getStorageRef(filePathAndName);
                        ref.putBytes(data)
                                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                        Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();
                                        while(!uriTask.isSuccessful());

                                        String downloadUri = uriTask.getResult().toString();
                                        if (uriTask.isSuccessful()) {
                                            Post postDb = new Post(postId, captionEt.getText().toString(),
                                                    descriptionEt.getText().toString(), downloadUri, timestamp,
                                                    post.getLikes(), post.getComments(), user.getUserId(),
                                                    user.getUsername(), user.getImage());
                                            DocumentReference ref = FirebaseUtils.getDocumentRef(Post.COLLECTION, postId);
                                            ref.set(postDb)
                                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                        @Override
                                                        public void onSuccess(Void unused) {
                                                            pd.dismiss();
                                                            Pop.pop(AddPostActivity.this, "Updated...");
                                                        }
                                                    })
                                                    .addOnFailureListener(new OnFailureListener() {
                                                        @Override
                                                        public void onFailure(@NonNull Exception e) {
                                                            pd.dismiss();
                                                            Pop.pop(AddPostActivity.this, e.getMessage());
                                                        }
                                                    });
                                        }
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        pd.dismiss();
                                        Pop.pop(AddPostActivity.this, e.getMessage());
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        pd.dismiss();
                        Pop.pop(AddPostActivity.this, e.getMessage());
                    }
                });
    }

    private void loadPostData(String postId) {
        CollectionReference ref = FirebaseUtils.getCollectionRef(Post.COLLECTION);
        Query query = ref.orderBy("postId").whereEqualTo("postId", postId);
        query.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                for (QueryDocumentSnapshot qds : queryDocumentSnapshots) {
                    post = qds.toObject(Post.class);

                    captionEt.setText(post.getCaption());
                    descriptionEt.setText(post.getDescription() == null ? post.getDescription() : "");

                    if (!post.getImage().equals("noImage")) {
                        try {
                            Picasso.get().load(post.getImage()).into(postImage);
                        } catch (Exception ex) {
                            Picasso.get().load(post.getImage()).into(postImage);
                        }
                    }
                }
            }
        });
    }

    private void uploadData(String caption, String description) {
        pd.setTitle("Publishing post...");
        pd.show();

        String timestamp = String.valueOf(System.currentTimeMillis());

        String filePathAndName = "Posts/" + "post_" + timestamp;

        if (postImage.getDrawable() != null) {
            Bitmap bitmap = ((BitmapDrawable)postImage.getDrawable()).getBitmap();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] data = baos.toByteArray();

            StorageReference ref = FirebaseStorage.getInstance().getReference().child(filePathAndName);
            ref.putBytes(data)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                          @Override
                          public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                              Task<Uri> uriTask = taskSnapshot.getStorage().getDownloadUrl();
                              while(!uriTask.isSuccessful());
                              String downloadUri = postImage.getDrawable() != null ? uriTask.getResult().toString() : "noImage";

                              if (uriTask.isSuccessful()) {
                                  Post postDb = new Post(timestamp, captionEt.getText().toString(),
                                          descriptionEt.getText().toString(), downloadUri, timestamp,
                                          "0", "0", user.getUserId(),
                                          user.getUsername(), user.getImage());
                                  FirebaseUtils.getDocumentRef(Post.COLLECTION, timestamp).set(postDb)
                                          .addOnSuccessListener(new OnSuccessListener<Void>() {
                                              @Override
                                              public void onSuccess(Void unused) {
                                                  pd.dismiss();
                                                  Pop.pop(AddPostActivity.this, "Posted");
                                                  startActivity(new Intent(AddPostActivity.this, MainActivity.class));
                                                  prepareNotification(
                                                          postId,
                                                          user.getUsername() + " add new post",
                                                          caption + "\n" + description,
                                                          "PostNotification",
                                                          "POST");
                                                  finish();
                                              }
                                          })
                                          .addOnFailureListener(new OnFailureListener() {
                                              @Override
                                              public void onFailure(@NonNull Exception e) {
                                                  pd.dismiss();
                                                  Pop.pop(AddPostActivity.this, e.getMessage());
                                              }
                                          });
                              }
                          }
                      }
                    ).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            pd.dismiss();
                            Pop.pop(AddPostActivity.this, e.getMessage());
                        }
                    });
        } else {
            Post postDb = new Post(timestamp, captionEt.getText().toString(),
                    descriptionEt.getText().toString(), "noImage", timestamp,
                    "0", "0", user.getUserId(),
                    user.getUsername(), user.getImage());
            FirebaseUtils.getDocumentRef(Post.COLLECTION, timestamp).set(postDb)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            pd.dismiss();
                            Pop.pop(AddPostActivity.this, "Posted");
                            startActivity(new Intent(AddPostActivity.this, MainActivity.class));
                            prepareNotification(
                                    postId,
                                    user.getUsername() + " add new post",
                                    caption + "\n" + description,
                                    "PostNotification",
                                    "POST");
                            finish();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            pd.dismiss();
                            Pop.pop(AddPostActivity.this, e.getMessage());
                        }
                    });
        }
    }

    private void prepareNotification(String postId, String caption, String description,
                                     String notificationType, String notificationTopic) {
        String NOTIFICATION_TOPIC = "/topics/" + notificationTopic;
        String NOTIFICATION_TITLE = caption;
        String NOTIFICATION_MESSAGE = description;
        String NOTIFICATION_TYPE = notificationType;

        JSONObject notificationJo = new JSONObject();
        JSONObject notificationBodyJo = new JSONObject();

        try {
            notificationBodyJo.put("notificationType", NOTIFICATION_TYPE);
            notificationBodyJo.put("sender", user.getUserId());
            notificationBodyJo.put("postId", postId);
            notificationBodyJo.put("pCaption", NOTIFICATION_TITLE);
            notificationBodyJo.put("pDescription", NOTIFICATION_MESSAGE);

            notificationJo.put("to", NOTIFICATION_TOPIC);
            notificationJo.put("data", notificationBodyJo);
        } catch (JSONException e) {
            Pop.pop(this, e.getMessage());
        }

        sendPostNotification(notificationJo);
    }

    private void sendPostNotification(JSONObject notificationJo) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest("https://fcm.googleapis.com/fcm/send", notificationJo,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("FCM_RESPONSE", "onResponse: " + response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        Pop.pop(AddPostActivity.this, volleyError.toString());
                    }
                })
        {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> header = new HashMap<>();
                header.put("Content-Type", "application/json");
                header.put("Authorization", "key=de6b13a7be301d2603174ebfa13084512d369acf");
                return header;
            }
        };
        Volley.newRequestQueue(this).add(jsonObjectRequest);
    }

    private void showImagePickDialog() {
        String[] options = {"Camera", "Gallery"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn ảnh từ");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (!checkCameraPermission()) {
                            requestCameraPermission();
                            requestStoragePermission();
                        } else {
                            pickFromCamera();
                        }
                    }
                }
                if (which == 1) {
                    pickFromGallery();
                }
            }
        });

        builder.create().show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAMERA_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean storageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    if (cameraAccepted && storageAccepted) {
                        pickFromCamera();
                    }
                } else {
                    Pop.pop(this, "Camera & Storage both permission are neccessary");
                }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this,
                        Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED) &&
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_MEDIA_VIDEO) == (PackageManager.PERMISSION_GRANTED);
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, cameraPermissions, CAMERA_REQUEST_CODE);
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_REQUEST_CODE);
    }

    private void pickFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, IMAGE_PICK_REQUEST_CODE);
    }

    private void pickFromCamera() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.TITLE, "Temp Pick");
        contentValues.put(MediaStore.Images.Media.DESCRIPTION, "Temp Desc");

        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

        Intent intent  = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, CAMERA_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA_REQUEST_CODE) {
                postImage.setImageURI(imageUri);
            }
            else if (requestCode == IMAGE_PICK_REQUEST_CODE) {
                imageUri = data.getData();
                postImage.setImageURI(imageUri);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}