package com.example.jfrmerger;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MainController {

    private final RecordRepository repository;

    @GetMapping("/record")
    public Collection<JfrRecord> getRecords() {
        return repository.findRecords();
    }

    @DeleteMapping("/record/{id}")
    public List<String> getRecords(@PathVariable String id) {
        return repository.remove(id);
    }


    @PostMapping("/record")
    public void saveRecord(@RequestParam("file") MultipartFile file) {
        repository.saveRecord(file);
    }
}
