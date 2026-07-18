package com.bluepath.app.network;

import retrofit2.Call;
import okhttp3.MultipartBody;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.POST;

public interface BluePathApi {
    @GET("api/v1/auth/nickname-available")
    Call<ApiModels.NicknameAvailability> nicknameAvailable(@Query("nickname") String nickname);

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

    @POST("api/v1/ai/search")
    Call<ApiModels.AiSearchResponse> aiSearch(
            @Header("Authorization") String authorization,
            @Body ApiModels.AiSearchRequest request
    );

    @GET("api/v1/dashboard")
    Call<ApiModels.DashboardResponse> dashboard(@Header("Authorization") String authorization);

    @Multipart
    @POST("api/v1/profile/image")
    Call<ApiModels.ProfileImageResponse> uploadProfileImage(
            @Header("Authorization") String authorization,
            @Part MultipartBody.Part file
    );

    @GET("api/v1/community/posts")
    Call<java.util.List<ApiModels.CommunityPostDto>> communityPosts(
            @Header("Authorization") String authorization,
            @Query("category") String category
    );

    @POST("api/v1/community/posts")
    Call<ApiModels.CommunityPostDto> createCommunityPost(
            @Header("Authorization") String authorization,
            @Body ApiModels.CommunityPostRequest request
    );

    @POST("api/v1/community/posts/{postId}/comments")
    Call<ApiModels.CommunityCommentDto> createCommunityComment(
            @Header("Authorization") String authorization,
            @Path("postId") String postId,
            @Body ApiModels.CommunityCommentRequest request
    );

    @POST("api/v1/community/reactions")
    Call<ApiModels.ReactionToggleResponse> toggleReaction(
            @Header("Authorization") String authorization,
            @Body ApiModels.ReactionToggleRequest request
    );

    @POST("api/v1/community/users/{userId}/follow")
    Call<ApiModels.FollowResponse> toggleFollow(
            @Header("Authorization") String authorization,
            @Path("userId") String userId
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

    @POST("api/v1/routes/plan")
    Call<ApiModels.RoutePlanResponse> planRoute(
            @Header("Authorization") String authorization,
            @Body ApiModels.RoutePlanRequest request
    );

    @POST("api/v1/routes/simulate")
    Call<ApiModels.RouteSimulationResponse> simulateRoute(
            @Header("Authorization") String authorization,
            @Body ApiModels.RouteSimulationRequest request
    );

    @POST("api/v1/routes/reroute")
    Call<ApiModels.RoutePlanResponse> reroute(
            @Header("Authorization") String authorization,
            @Body ApiModels.RouteRerouteRequest request
    );

    @POST("api/v1/routes/outcomes")
    Call<ApiModels.GenericResponse> routeOutcome(
            @Header("Authorization") String authorization,
            @Body ApiModels.RouteOutcomeRequest request
    );

    @POST("api/v1/missions/generate")
    Call<ApiModels.FamilyMissionResponse> generateMission(
            @Header("Authorization") String authorization,
            @Body ApiModels.MissionGenerateRequest request
    );

    @POST("api/v1/missions/verify")
    Call<ApiModels.MissionVerifyResponse> verifyMission(
            @Header("Authorization") String authorization,
            @Body ApiModels.MissionVerifyRequest request
    );

}
