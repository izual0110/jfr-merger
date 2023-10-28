package com.jfrmerger.backend;

import com.jfrmerger.common.RecordRepository;
import com.jfrmerger.common.model.JfrRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

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
    public void getRecords(@PathVariable String id) {
        repository.remove(id);
    }


    @PostMapping("/record")
    public void saveRecord(@RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream stream = file.getInputStream()) {
            repository.saveRecord(stream, file.getName());
        }
    }
}
