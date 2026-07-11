package com.bluepath.app.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface BluePathApi {
    @POST("api/v1/auth/register")
    Call<ApiModels.AuthResponse> register(@Body ApiModels.AuthRequest request);

    @POST("api/v1/auth/login")
    Call<ApiModels.AuthResponse> login(@Body ApiModels.AuthRequest request);

    @POST("api/v1/auth/password-reset/request")
    Call<ApiModels.GenericResponse> requestPasswordReset(@Body ApiModels.PasswordResetRequest request);

    @POST("api/v1/ai/quiz")
    Call<ApiModels.QuizResponse> generateQuiz(
            @Header("Authorization") String authorization,
            @Body ApiModels.QuizRequest request
    );

    @POST("api/v1/ai/agent")
    Call<ApiModels.AgentResponse> answerAgent(
            @Header("Authorization") String authorization,
            @Body ApiModels.AgentRequest request
    );

    @GET("api/v1/catalog")
    Call<java.util.List<ApiModels.ContentDto>> catalog(@Header("Authorization") String authorization);

    @GET("api/v1/sync")
    Call<ApiModels.CloudStateResponse> cloudState(@Header("Authorization") String authorization);

    @POST("api/v1/sync")
    Call<ApiModels.SyncResponse> sync(
            @Header("Authorization") String authorization,
            @Body ApiModels.SyncRequest request
    );

    @POST("api/v1/diamond/evidence")
    Call<ApiModels.GenericResponse> submitEvidence(
            @Header("Authorization") String authorization,
            @Body ApiModels.EvidenceRequest request
    );

    @GET("api/v1/diamond/status")
    Call<ApiModels.DiamondStatus> diamondStatus(@Header("Authorization") String authorization);
}
