package org.baeldung.web.controller.rest;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.baeldung.persistence.model.Post;
import org.baeldung.persistence.model.SubmissionResponse;
import org.baeldung.persistence.model.User;
import org.baeldung.security.UserPrincipal;
import org.baeldung.service.IScheduledPostService;
import org.baeldung.web.ScheduledPostDto;
import org.baeldung.web.SubmissionResponseDto;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping(value = "/api/scheduledPosts")
class ScheduledPostRestController {

    @Autowired
    private IScheduledPostService scheduledPostService;

    @Autowired
    private ModelMapper modelMapper;

    public ScheduledPostRestController() {
        super();
    }

    // API - read

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public final List<ScheduledPostDto> getScheduledPosts(@RequestParam(value = "page", required = false, defaultValue = "0") final int page, @RequestParam(value = "size", required = false, defaultValue = "10") final int size,
            @RequestParam(value = "sortDir", required = false, defaultValue = "asc") final String sortDir, @RequestParam(value = "sort", required = false, defaultValue = "title") final String sort, final HttpServletResponse response) {
        response.addHeader("PAGING_INFO", scheduledPostService.generatePagingInfo(page, size).toString());
        final List<Post> posts = scheduledPostService.getPostsList(page, size, sortDir, sort);
        return posts.stream().map(post -> convertToDto(post)).collect(Collectors.toList());
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ResponseBody
    public final ScheduledPostDto getPost(@PathVariable("id") final Long id) {
        return convertToDto(scheduledPostService.getPostById(id));
    }

    @RequestMapping(value = "/available")
    @ResponseBody
    public final String countAvailablePostsToSchedule(final HttpServletRequest request) {
        if (request.isUserInRole("POST_UNLIMITED_PRIVILEGE")) {
            return "You can schedule as many posts as you want";
        }
        return "You can schedule maximium " + scheduledPostService.countAvailablePostsToSchedule() + " posts to be submitted today";
    }

    // API - write

    @RequestMapping(method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public final ScheduledPostDto schedule(final HttpServletRequest request, @RequestBody final ScheduledPostDto postDto, @RequestParam(value = "resubmitOptionsActivated") final boolean resubmitOptionsActivated) throws ParseException {
        final Post post = convertToEntity(postDto);
        final Post postCreated = scheduledPostService.schedulePost(request.isUserInRole("POST_UNLIMITED_PRIVILEGE"), post, resubmitOptionsActivated);
        return convertToDto(postCreated);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    public final void updatePost(final HttpServletRequest request, @RequestBody final ScheduledPostDto postDto, @RequestParam(value = "resubmitOptionsActivated") final boolean resubmitOptionsActivated) throws ParseException {
        final Post post = convertToEntity(postDto);
        scheduledPostService.updatePost(request.isUserInRole("POST_UNLIMITED_PRIVILEGE"), post, resubmitOptionsActivated);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public final void deletePost(@PathVariable("id") final Long id) {
        scheduledPostService.deletePostById(id);
    }

    //

    private ScheduledPostDto convertToDto(final Post post) {
        final ScheduledPostDto postDto = modelMapper.map(post, ScheduledPostDto.class);
        postDto.setSubmissionDate(post.getSubmissionDate(), getCurrentUser().getPreference().getTimezone());
        final List<SubmissionResponse> response = post.getSubmissionsResponse();
        if ((response != null) && (response.size() > 0)) {
            postDto.setStatus(response.get(response.size() - 1).toString().substring(0, 30));
            final List<SubmissionResponseDto> responsedto = post.getSubmissionsResponse().stream().map(res -> generateResponseDto(res)).collect(Collectors.toList());
            postDto.setDetailedStatus(responsedto);
        } else {
            postDto.setStatus("Not sent yet");
            postDto.setDetailedStatus(Collections.emptyList());
        }
        return postDto;
    }

    private SubmissionResponseDto generateResponseDto(final SubmissionResponse responseEntity) {
        final SubmissionResponseDto dto = modelMapper.map(responseEntity, SubmissionResponseDto.class);
        final String timezone = getCurrentUser().getPreference().getTimezone();
        dto.setLocalSubmissionDate(responseEntity.getSubmissionDate(), timezone);
        if (responseEntity.getScoreCheckDate() != null) {
            dto.setLocalScoreCheckDate(responseEntity.getScoreCheckDate(), timezone);
        }
        return dto;
    }

    private Post convertToEntity(final ScheduledPostDto postDto) throws ParseException {
        final Post post = modelMapper.map(postDto, Post.class);
        post.setSubmissionDate(postDto.getSubmissionDateConverted(getCurrentUser().getPreference().getTimezone()));
        if (postDto.getId() != null) {
            final Post oldPost = scheduledPostService.getPostById(postDto.getId());
            post.setRedditID(oldPost.getRedditID());
            post.setSent(oldPost.isSent());
            post.setSubmissionsResponse(post.getSubmissionsResponse());
        }
        return post;
    }

    //
    private User getCurrentUser() {
        final UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userPrincipal.getUser();
    }

}
