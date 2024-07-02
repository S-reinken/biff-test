// When plain htmx isn't quite enough, you can stick some custom JS here.

htmx.onLoad(function(content) {
  const csrfToken = document.body.querySelector("[name~=__anti-forgery-token]").value;
  let createSortable = (group) => (element) => (
    new Sortable(element, {
        animation: 150,
        group: group,
        onEnd: function (evt) {
            var to = evt.to;
            var from = evt.from
            var to_id = to.getAttribute('uuid');
            var from_id = from.getAttribute('uuid');
            let destination_cards = Array.from(to.children).map((child, index) => 
              ({
                title: child.getAttribute('title'),
                id: child.getAttribute('uuid'),
                position: index
              }))
            let source_cards = Array.from(from.children).map((child, index) => 
              ({
                title: child.getAttribute('title'),
                id: child.getAttribute('uuid'),
                position: index
              }))
            
  
            var url = "/app/trello/move-card";
            var data = new FormData();
            data.append('source-list-id', from_id);
            data.append('destination-list-id', to_id);
            data.append('source-cards', JSON.stringify(source_cards));
            data.append('destination-cards', JSON.stringify(destination_cards));
            fetch(url, {
                headers: {
                    "X-CSRF-Token": csrfToken
                },
                method: 'POST',
                body: data
            });
        }
    })
  )
  var sortable_lists = content.querySelectorAll(".sortable-list");
  var sortable_boards = content.querySelectorAll(".sortable-board");
  Array.from(sortable_lists).map(createSortable('cards'));
  Array.from(sortable_boards).map(createSortable('lists'));
})