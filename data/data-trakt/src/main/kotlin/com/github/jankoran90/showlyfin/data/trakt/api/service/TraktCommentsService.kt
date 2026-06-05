package com.github.jankoran90.showlyfin.data.trakt.api.service

import com.github.jankoran90.showlyfin.data.trakt.model.Comment
import com.github.jankoran90.showlyfin.data.trakt.model.request.CommentRequest
import retrofit2.Response
import retrofit2.http.*

interface TraktCommentsService {
    @GET("comments/{id}/replies")
    suspend fun fetchCommentReplies(@Path("id") commentId: Long, @Query("timestamp") timestamp: Long): List<Comment>

    @POST("comments")
    suspend fun postComment(@Body commentBody: CommentRequest): Comment

    @POST("comments/{id}/replies")
    suspend fun postCommentReply(@Path("id") commentId: Long, @Body commentBody: CommentRequest): Comment

    @DELETE("comments/{id}")
    suspend fun deleteComment(@Path("id") commentId: Long): Response<Any>
}
