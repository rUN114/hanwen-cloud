package org.hanwen.common;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "hw-user-service", contextId = "display-name-directory")
public interface DirectoryClient {
    @PostMapping("/internal/users/students-by-username")
    java.util.List<Contracts.StudentAccount> students(
            @RequestBody java.util.List<String> usernames);

    @GetMapping("/internal/users/teachers/{id}")
    Contracts.StudentAccount teacher(@PathVariable("id") long id);

    @PostMapping("/internal/users/teachers-by-username")
    java.util.List<Contracts.StudentAccount> teachers(
            @RequestBody java.util.List<String> usernames);

    @GetMapping("/internal/users/display-names")
    List<Contracts.PublicUser> names(@RequestParam("ids") String ids);
}
